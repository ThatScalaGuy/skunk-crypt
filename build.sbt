// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization := "de.thatscalaguy"
ThisBuild / organizationName := "ThatScalaGuy"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("ThatScalaGuy", "Sven Herrmann")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

val Scala213 = "2.13.14"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.3")
ThisBuild / scalaVersion := Scala213 // the default Scala

lazy val root = (project in file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "skunk-crypt",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.12.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "org.tpolecat" %% "skunk-core" % "1.0.0-M7",
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % "0.41.4" % Test
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
