lazy val commonSettings = Seq (
    version := "0.1",
    organization := "openquant",
    scalaVersion := "2.11.7",
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8"),
    resolvers ++= Seq(
        Resolver.sonatypeRepo("releases"),
        Resolver.sonatypeRepo("snapshots")
    )
)

lazy val testDependencies = Seq(
    "org.specs2" %% "specs2" % "3.+" % "test"
)

lazy val commonDependencies = Seq(
    "org.scalaz" %% "scalaz-core" % "7.2.0",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
    "commons-logging" % "commons-logging" % "99-empty",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.iheart" %% "ficus" % "1.2.3",
    "openquant" %% "common" % "+"
)

lazy val quoteprovider = project.in(file("quoteprovider"))
    .settings(commonSettings:_*)
    .settings(libraryDependencies ++= testDependencies)
    .settings(libraryDependencies ++= commonDependencies)

lazy val yahooquoteprovider = project.in(file("yahooquoteprovider"))
    .settings(commonSettings:_*)
    .settings(libraryDependencies ++= testDependencies)
    .settings(libraryDependencies ++= commonDependencies)
    .settings(libraryDependencies ++= {
            Seq(
                "com.larroy.openquant" %% "yahoofinancescala" % "0.2"
            )
        }
    )
    .dependsOn(quoteprovider)


lazy val symbols = project.in(file("symbols"))
    .settings(commonSettings:_*)
    .settings(libraryDependencies ++= testDependencies)
    .settings(libraryDependencies ++= commonDependencies)
    .settings(libraryDependencies ++= {
            Seq(
                "com.github.tototoshi" %% "scala-csv" % "1.+"
            )
        }
    )

lazy val cli = project.in(file("cli"))
    .settings(commonSettings:_*)
    .settings(libraryDependencies ++= testDependencies)
    .settings(libraryDependencies ++= commonDependencies)
    .settings(libraryDependencies ++= {
            Seq(
                "com.github.scopt" %% "scopt" % "3.2.0"
            )
        }
    )
    .dependsOn(quoteprovider, symbols, quotedb, yahooquoteprovider)

lazy val quotedb = project.in(file("quotedb"))
    .settings(commonSettings:_*)
    .settings(libraryDependencies ++= testDependencies)
    .settings(libraryDependencies ++= commonDependencies)
    .settings(libraryDependencies ++= {
            Seq(
                "org.scalikejdbc" %% "scalikejdbc" % "2.3.+",
                "org.xerial" % "sqlite-jdbc" % "3.8.7"
            )
        }
    )

lazy val main = project.in(file("."))
    .settings(commonSettings:_*)
    .aggregate(quoteprovider, symbols, yahooquoteprovider)


parallelExecution in Test := false


