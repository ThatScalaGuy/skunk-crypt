lazy val V = new {
  val Scala213        = "2.13.16"
  val Scala3          = "3.3.6"
  val Skunk           = "1.0.0-M10"
  val Cats            = "2.13.0"
  val CatsEffect      = "3.6.1"
  val Munit           = "1.0.0"
  val MunitCatsEffect = "2.0.0"
  val Testcontainers  = "0.41.4"
}

// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization     := "de.thatscalaguy"
ThisBuild / organizationName := "ThatScalaGuy"
ThisBuild / startYear        := Some(2024)
ThisBuild / licenses         := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("ThatScalaGuy", "Sven Herrmann")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

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
      "org.typelevel" %% "cats-core"                  % V.Cats       % "provided",
      "org.typelevel" %% "cats-effect"                % V.CatsEffect % "provided",
      "org.tpolecat"  %% "skunk-core"                 % V.Skunk      % "provided",
      "org.scalameta" %% "munit"                      % "1.1.1"      % Test,
      "org.typelevel" %% "munit-cats-effect"          % "2.1.0"      % Test,
      "com.dimafeng"  %% "testcontainers-scala-munit" % "0.43.0"     % Test
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
