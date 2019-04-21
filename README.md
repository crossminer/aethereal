# aethereal
[![Build Status](https://travis-ci.com/tdegueul/aethereal.svg?branch=master)](https://travis-ci.com/tdegueul/aethereal)

## Usage
```
usage: aethereal
 -artifactId <artifactId>   artifactId of the artifact to be analyzed
 -download                  Download all JARs locally
 -downloadPath <path>       Relative path to where the dataset should be
                            downloaded
 -groupId <groupId>         groupId of the artifact to be analyzed
 -local                     Fetch artifact information from a local copy
                            of the Maven Dependency Graph
 -m3                        Serialize the M3 models of all JARs
 -remote                    Fetch artifact information from Maven Central
                            / mvnrepository.com
```

## Examples

From Maven:

```
mvn exec:java -Dexec.mainClass="nl.cwi.swat.aethereal.Main" -Dexec.args="-local -download -m3 -groupId org.sonarsource.sonarqube -artifactId sonar-plugin-api"
```

