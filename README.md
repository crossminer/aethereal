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

## Example matrix output
```
For library org.sonarsource.sonarqube:sonar-plugin-api
Number of versions: 55 [min: 5.2-RC3, max: 6.7-RC1]
Number of clients: 365
Number of links: 365
Orphan libraries: 4 [[org.sonarsource.sonarqube:sonar-plugin-api:jar:5.3-RC1, org.sonarsource.sonarqube:sonar-plugin-api:jar:5.3-RC2, org.sonarsource.sonarqube:sonar-plugin-api:jar:5.3, org.sonarsource.sonarqube:sonar-plugin-api:jar:5.2]]
Matrix density: 0.01818181818181818
Clients per library: [avg: 5.927272727272728, min: 0, max: 38]
```

## Examples

From Maven:

```
mvn exec:java -Dexec.mainClass="nl.cwi.swat.aethereal.Main" -Dexec.args="-local -download -m3 -groupId org.sonarsource.sonarqube -artifactId sonar-plugin-api"
```

