# aethereal
[![Build Status](https://travis-ci.com/tdegueul/aethereal.svg?branch=master)](https://travis-ci.com/tdegueul/aethereal)

## Usage
```
usage: aethereal
 -artifactId <artifactId>   artifactId of the artifact to be analyzed
 -download                  Set this option to download all JARs locally
 -groupId <groupId>         groupId of the artifact to be analyzed
 -local                     Fetch artifact information from a local copy
                            of the Maven Dependency Graph
 -remote                    Fetch artifact information from Maven Central
                            / mvnrepository.com
```

## Examples

`aethereal -local -groupId org.sonarsource.sonarqube -artifactId sonar-plugin-api`

