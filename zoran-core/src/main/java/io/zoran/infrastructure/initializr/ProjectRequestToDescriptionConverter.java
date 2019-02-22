package io.zoran.infrastructure.initializr;

import io.spring.initializr.generator.buildsystem.BuildSystem;
import io.spring.initializr.generator.language.Language;
import io.spring.initializr.generator.packaging.Packaging;
import io.spring.initializr.generator.project.ProjectDescription;
import io.spring.initializr.generator.spring.build.MetadataBuildItemMapper;
import io.spring.initializr.generator.version.Version;
import io.spring.initializr.metadata.DefaultMetadataElement;
import io.spring.initializr.metadata.Dependency;
import io.spring.initializr.metadata.InitializrMetadata;
import io.spring.initializr.metadata.Type;
import io.zoran.domain.initializr.ProjectRequest;
import io.zoran.infrastructure.exception.ZoranHandlerException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Michal Sadowski (michal.sadowski@roche.com) on 21.02.2019
 */
@Service
public class ProjectRequestToDescriptionConverter {
    private static final Version VERSION_1_5_0 = Version.parse("1.5.0.RELEASE");

    public ProjectDescription convert(ProjectRequest request,
                                      InitializrMetadata metadata) {
        validate(request, metadata);
        String springBootVersion = getSpringBootVersion(request, metadata);
        List<Dependency> resolvedDependencies = getResolvedDependencies(request,
                springBootVersion, metadata);
        validateDependencyRange(springBootVersion, resolvedDependencies);
        ProjectDescription description = new ProjectDescription();
        description.setApplicationName(getApplicationName(request, metadata));
        description.setArtifactId(determineValue(request.getArtifactId(),
                () -> metadata.getArtifactId().getContent()));
        description.setBaseDirectory(request.getBaseDir());
        description.setBuildSystem(getBuildSystem(request, metadata));
        description.setDescription(determineValue(request.getDescription(),
                () -> metadata.getDescription().getContent()));
        description.setGroupId(determineValue(request.getGroupId(),
                () -> metadata.getGroupId().getContent()));
        description.setLanguage(
                Language.forId(request.getLanguage(), request.getJavaVersion()));
        description.setName(
                determineValue(request.getName(), () -> metadata.getName().getContent()));
        description.setPackageName(getPackageName(request, metadata));
        description.setPackaging(Packaging.forId(request.getPackaging()));
        description.setPlatformVersion(Version.parse(springBootVersion));
        description.setVersion(determineValue(request.getVersion(),
                () -> metadata.getVersion().getContent()));
        resolvedDependencies
                .forEach((dependency) -> description.addDependency(dependency.getId(),
                        MetadataBuildItemMapper.toDependency(dependency)));
        return description;
    }

    private String determineValue(String candidate, Supplier<String> fallback) {
        return (StringUtils.hasText(candidate)) ? candidate : fallback.get();
    }

    private void validate(ProjectRequest request, InitializrMetadata metadata) {
        validateSpringBootVersion(request);
        validateType(request.getType(), metadata);
        validateLanguage(request.getLanguage(), metadata);
        validatePackaging(request.getPackaging(), metadata);
        validateDependencies(request, metadata);
    }

    private void validateSpringBootVersion(ProjectRequest request) {
        Version bootVersion = Version.safeParse(request.getBootVersion());
        if (bootVersion != null && bootVersion.compareTo(VERSION_1_5_0) < 0) {
            throw new ZoranHandlerException("Invalid Spring Boot version "
                    + bootVersion + " must be 1.5.0 or higher");
        }
    }

    private void validateType(String type, InitializrMetadata metadata) {
        if (type != null) {
            Type typeFromMetadata = metadata.getTypes().get(type);
            if (typeFromMetadata == null) {
                throw new ZoranHandlerException(
                        "Unknown type '" + type + "' check project metadata");
            }
            if (!typeFromMetadata.getTags().containsKey("build")) {
                throw new ZoranHandlerException("Invalid type '" + type
                        + "' (missing build tag) check project metadata");
            }
        }
    }

    private void validateLanguage(String language, InitializrMetadata metadata) {
        if (language != null) {
            DefaultMetadataElement languageFromMetadata = metadata.getLanguages()
                                                                  .get(language);
            if (languageFromMetadata == null) {
                throw new ZoranHandlerException(
                        "Unknown language '" + language + "' check project metadata");
            }
        }
    }

    private void validatePackaging(String packaging, InitializrMetadata metadata) {
        if (packaging != null) {
            DefaultMetadataElement packagingFromMetadata = metadata.getPackagings()
                                                                   .get(packaging);
            if (packagingFromMetadata == null) {
                throw new ZoranHandlerException(
                        "Unknown packaging '" + packaging + "' check project metadata");
            }
        }
    }

    private void validateDependencies(ProjectRequest request,
                                      InitializrMetadata metadata) {
        List<String> dependencies = (!request.getStyle().isEmpty() ? request.getStyle()
                : request.getDependencies());
        dependencies.forEach((dep) -> {
            Dependency dependency = metadata.getDependencies().get(dep);
            if (dependency == null) {
                throw new ZoranHandlerException(
                        "Unknown dependency '" + dep + "' check project metadata");
            }
        });
    }

    private void validateDependencyRange(String springBootVersion,
                                         List<Dependency> resolvedDependencies) {
        resolvedDependencies.forEach((dep) -> {
            if (!dep.match(Version.parse(springBootVersion))) {
                throw new ZoranHandlerException(
                        "Dependency '" + dep.getId() + "' is not compatible "
                                + "with Spring Boot " + springBootVersion);
            }
        });
    }

    private BuildSystem getBuildSystem(ProjectRequest request,
                                       InitializrMetadata metadata) {
        Type typeFromMetadata = metadata.getTypes().get(request.getType());
        return BuildSystem.forId(typeFromMetadata.getTags().get("build"));
    }

    private String getPackageName(ProjectRequest request, InitializrMetadata metadata) {
        return metadata.getConfiguration().cleanPackageName(request.getPackageName(),
                metadata.getPackageName().getContent());
    }

    private String getApplicationName(ProjectRequest request,
                                      InitializrMetadata metadata) {
        if (!StringUtils.hasText(request.getApplicationName())) {
            return metadata.getConfiguration().generateApplicationName(request.getName());
        }
        return request.getApplicationName();
    }

    private String getSpringBootVersion(ProjectRequest request,
                                        InitializrMetadata metadata) {
        return (request.getBootVersion() != null) ? request.getBootVersion()
                : metadata.getBootVersions().getDefault().getId();
    }

    private List<Dependency> getResolvedDependencies(ProjectRequest request,
                                                     String springBootVersion, InitializrMetadata metadata) {
        List<String> depIds = (!request.getStyle().isEmpty() ? request.getStyle()
                : request.getDependencies());
        Version requestedVersion = Version.parse(springBootVersion);
        return depIds.stream().map((it) -> {
            Dependency dependency = metadata.getDependencies().get(it);
            return dependency.resolve(requestedVersion);
        }).collect(Collectors.toList());
    }
}
