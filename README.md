## Scala HttpService sample using Pekko/Akka Framework with KeyCloak SSO Integration
- ### KeyCloak features
- - #### Validate Token
- - #### Verify User Email via Code
- - #### Get User Info, Roles, Groups by Token
- - #### Register Users
- - #### List Users
- - #### List Groups
- - #### Change User Groups
- ### Also includes
- - #### Mail Client
- - #### PostgreSQL HikariCP Slick
- - #### Json Circe.IO


## Installation and running

>Compile and run this project using `sbt run`

## Docker Building

>This project contains file `project/plugins.sbt` which installs native plugin to build docker image

>Modify docker name in `build.sbt`

>Build docker with `sbt docker:publishLocal` or `sbt docker:publish` to deploy it to your local or remote machine