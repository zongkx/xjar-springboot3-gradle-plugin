package io.github.zongkx;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class XJarExtension {
    public abstract ListProperty<String> getIncludes();
    public abstract ListProperty<String> getExcludes();
    public abstract Property<String> getGoPath();
    public abstract RegularFileProperty getSourceJar();
    public abstract RegularFileProperty getTargetJar();
}