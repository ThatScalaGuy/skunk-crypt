lazy val V = new {
  val Scala213        = "2.13.18"
  val Scala3          = "3.3.7"
  val Skunk           = "1.0.0"
  val Cats            = "2.13.0"
  val CatsEffect      = "3.7.0"
  val Munit           = "1.3.2"
  val MunitCatsEffect = "2.1.0"
  val Testcontainers  = "0.43.6"
}

// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "1.0" // your current series x.y

ThisBuild / organization     := "de.thatscalaguy"
ThisBuild / organizationName := "ThatScalaGuy"
ThisBuild / startYear        := Some(2024)
ThisBuild / licenses         := Seq(License.Apache2)
ThisBuild / developers       := List(
  // your GitHub handle and name
  tlGitHubDev("ThatScalaGuy", "Sven Herrmann")
)

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

ThisBuild / crossScalaVersions := Seq(V.Scala213, V.Scala3)
ThisBuild / scalaVersion       := V.Scala213 // the default Scala

Test / fork                        := true
Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat

lazy val root = (project in file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "skunk-crypt",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"                  % V.Cats            % "provided",
      "org.typelevel" %% "cats-effect"                % V.CatsEffect      % "provided",
      "org.tpolecat"  %% "skunk-core"                 % V.Skunk           % "provided",
      "org.scalameta" %% "munit"                      % V.Munit           % Test,
      "org.typelevel" %% "munit-cats-effect"          % V.MunitCatsEffect % Test,
      "com.dimafeng"  %% "testcontainers-scala-munit" % V.Testcontainers  % Test
    )
  )

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(tlSiteHelium ~= {
    import laika.helium.config._
    import laika.ast.Path.Root
    _.site
      .topNavigationBar(
        homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home)
      )
  })
