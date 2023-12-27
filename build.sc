import mill.{Agg, _}
import mill.scalalib._
import $file.dependencies
import dependencies.Dependencies
import $ivy.`io.github.davidgregory084::mill-tpolecat::0.3.5`
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import io.github.davidgregory084.TpolecatModule
import io.kipp.mill.ci.release.CiReleaseModule
import io.kipp.mill.ci.release.SonatypeHost
import mill.scalalib.publish._

val scalaVersions = Seq("2.13.12", "3.3.1")

trait spake2p extends TpolecatModule {
//  def scalaVersion = "2.13.12"

  override def scalacPluginIvyDeps: T[Agg[Dep]] = super.scalacPluginIvyDeps() ++ Agg(
    Dependencies.plugins.betterMonadicFor,
    Dependencies.plugins.kindProjector,
  )

  override def scalacOptions: T[Seq[String]] = (super.scalacOptions() ++ Seq(
    "-Ymacro-annotations",
    "-Xsource:3",
  )).filterNot(o => Set(
    "-Xfatal-warnings",
  ).contains(o))

  override def ivyDeps = Agg(
    Dependencies.fs2.core,
    Dependencies.bouncycastle.pkix,
  )

  override def runIvyDeps = super.runIvyDeps() ++ Agg(
    Dependencies.bouncycastle.provider,
  )
}

object core extends Cross[CoreModule]("2.13.12")
trait CoreModule extends CrossScalaModule with spake2p with CiReleaseModule {
  override def sonatypeHost = Some(SonatypeHost.s01)

  object test extends ScalaTests with TestModule.Munit {
    override def ivyDeps = Agg(
      Dependencies.test.munit,
    )
  }

  def pomSettings = T {
    PomSettings(
      description = "Provide cats-effect additional convenience methods for Spake2Plus4s",
      organization = "io.yellowstonesoftware.spake2plus4s",
      url = "https://github.com/yellowstonesoftware/spake2plus4s",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("yellowstore-software", "spake2plus4s"),
      developers = Seq(Developer("yellowstone-software", "Peter vR", "https.//github.com/yellowstone-software"))
    )
  }
}

object `cats-effect` extends Cross[CatsEffectModule]("2.13.12")
trait CatsEffectModule extends CrossScalaModule with spake2p with CiReleaseModule {
  override def sonatypeHost = Some(SonatypeHost.s01)

  override def moduleDeps = Seq(core())

  override def ivyDeps = super.runIvyDeps() ++ Agg(
    Dependencies.cats.core,
    Dependencies.cats.effect,
    Dependencies.cats.log4Cats,
  )

  object test extends ScalaTests with TestModule.Munit {
    override def ivyDeps = Agg(
      Dependencies.test.munit,
      Dependencies.test.munitCatsEffect,
    )
  }

  def pomSettings = T {
    PomSettings(
      description = "Provide cats-effect additional convenience methods for Spake2Plus4s",
      organization = "io.yellowstonesoftware.spake2plus4s",
      url = "https://github.com/yellowstonesoftware/spake2plus4s",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("yellowstore-software", "spake2plus4s"),
      developers = Seq(Developer("yellowstone-software", "Peter vR", "https.//github.com/yellowstone-software"))
    )
  }
}
