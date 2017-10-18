# v0.2 (10/20/2017)
# Release Notes

## Notable Changes
The Barcelona Release (v 0.2) of the MQTT micro service includes the following:
* Application of Google Style Guidelines to the code base
* POM changes for appropriate repository information for distribution/repos management, checkstyle plugins, etc.
* Added Dockerfile for creation of micro service targeted for ARM64
* Consolidated Docker properties files to common directory

## Bug Fixes
* Fixed Asynchronous data handling
* Fixed Docker service configuration
* Added check for service existence after initialization to Base Service

 - [#9](https://github.com/edgexfoundry/device-mqtt/pull/9) - Adds null check in BaseService contributed by Tyler Cox ([trcox](https://github.com/trcox))
 - [#8](https://github.com/edgexfoundry/device-mqtt/pull/8) - Add snapshots and staging repo definition contributed by Jeremy Phelps ([JPWKU](https://github.com/JPWKU))
 - [#7](https://github.com/edgexfoundry/device-mqtt/pull/7) - Fixes Maven artifact dependency path contributed by Tyler Cox ([trcox](https://github.com/trcox))
 - [#6](https://github.com/edgexfoundry/device-mqtt/pull/6) - Added support for aarch64 arch contributed by ([feclare](https://github.com/feclare))
 - [#5](https://github.com/edgexfoundry/device-mqtt/pull/5) - Fixes Service to run with SDK Updates contributed by Tyler Cox ([trcox](https://github.com/trcox))
 - [#4](https://github.com/edgexfoundry/device-mqtt/pull/4) - Adds Docker build capability contributed by Tyler Cox ([trcox](https://github.com/trcox))
 - [#3](https://github.com/edgexfoundry/device-mqtt/pull/3) - Add distributionManagement for artifact storage contributed by Tyler Cox ([trcox](https://github.com/trcox))
 - [#2](https://github.com/edgexfoundry/device-mqtt/issues/2) - Update to use Google Code Style
 - [#1](https://github.com/edgexfoundry/device-mqtt/pull/1) - Contributed Project Fuse source code contributed by Tyler Cox ([trcox](https://github.com/trcox))
