# key-abbrevmgr

This is an extension for the [KeY theorem prover](https://github.com/keyproject/key). 

An abbreviation is a short name, like `@name`, which represents a longer term. Abbreviations are built-in to the KeY system, but this construct is not very popular. This may be to their volatility: you are not able to store, and reuse them for new proof attempts.

This PR brings a new widget which allows a better management of abbreviations: 

![image](https://github.com/KeYProject/key/assets/104259/8583b869-4782-420f-86a5-a733729ffda7)

The widget offers following features:

* View the currently defined abbreviations
* Load and store abbreviation mappings in a JSON format.
* Transfer (import) abbreviations in the currently open proof
* Change, Delete, Disable abbreviations via the context menu

## Getting Started

key-abbrevmgr is available via maven central. To enable it, add following lines to `key.ui/build.gradle`:

### Released version

![Maven Central Version](https://img.shields.io/maven-central/v/io.github.waddon.key/key-abbrevmgr)

```kotlin
repositories {
    // for released versions
    mavenCentral()
}

dependencies {
    //...
    runtimeOnly("io.github.waddon.key:key-abbrevmgr:0.1")
}
```

### SNAPSHOT version


```kotlin
repositories {
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots")} 
}

dependencies {
    //...
    runtimeOnly("io.github.waddon.key:key-abbrevmgr:0.2-SNAPSHOT")
}
```

## Releasing `key-abbrevmgr`


1. Remove SNAPSHOT from version number
2. Update `README.md`
3. Create new commit and tag.
4. `gradle makeDownloadScript`
5. `bash download.sh && zip -r key-interaction-version.zip key-interaction-version`
6. `gradle publishToCentral closeAndReleaseCentralStagingRepository`
7. Create new GitHub release, upload `key-interaction-version.zip`
8. Set version++, re-add to `SNAPSHOT` and commit.

