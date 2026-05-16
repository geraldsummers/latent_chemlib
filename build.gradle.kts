import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    eclipse
    `maven-publish`
    jacoco
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
}

val minecraftVersion = property("minecraft_version") as String
val forgeVersion = property("forge_version") as String
val kotlinForForgeVersion = property("kotlinforforge_version") as String
val createReleaseVersion = property("create_release_version") as String
val createMavenVersion = property("create_maven_version") as String
val ponderVersion = property("ponder_version") as String
val flywheelVersion = property("flywheel_version") as String
val registrateVersion = property("registrate_version") as String
val chemlibVersion = property("chemlib_version") as String
val chemlibCurseFileId = property("chemlib_curse_file_id") as String
val alchemylibVersion = property("alchemylib_version") as String
val alchemylibCurseFileId = property("alchemylib_curse_file_id") as String
val alchemistryVersion = property("alchemistry_version") as String
val alchemistryCurseFileId = property("alchemistry_curse_file_id") as String
val createNewAgeVersion = property("create_new_age_version") as String
val createNewAgeCurseFileId = property("create_new_age_curse_file_id") as String
val emiVersion = property("emi_version") as String
val emiCurseFileId = property("emi_curse_file_id") as String
val jeiVersion = property("jei_version") as String
val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modVersion = property("mod_version") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String
val modLicense = property("mod_license") as String
val modIssueTrackerUrl = property("mod_issue_tracker_url") as String

group = property("mod_group") as String
version = modVersion

base {
    archivesName.set(modId)
}

fun deobf(notation: String): Any =
    requireNotNull(extensions.getByName("fg").withGroovyBuilder { "deobf"(notation) })

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

minecraft {
    mappings("official", minecraftVersion)
    copyIdeResources = true

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "info")
            property("forge.enabledGameTestNamespaces", "$modId,minecraft")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)

            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("client")

        create("server") {
            arg("--nogui")
        }

        create("gameTestServer")

        create("data") {
            args(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath
            )
        }
    }
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/mirror")
    maven("https://www.cursemaven.com") {
        content {
            includeGroup("curse.maven")
        }
    }
    maven("https://maven.blamejared.com")
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    implementation("thedarkcolour:kotlinforforge:$kotlinForForgeVersion")

    implementation(deobf("com.simibubi.create:create-$minecraftVersion:$createMavenVersion:slim"))
    implementation(deobf("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion"))
    compileOnly(deobf("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion"))
    runtimeOnly(deobf("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:$flywheelVersion"))
    implementation(deobf("com.tterrag.registrate:Registrate:$registrateVersion"))

    implementation(deobf("curse.maven:chemlib-340666:$chemlibCurseFileId"))
    implementation(deobf("curse.maven:alchemylib-293426:$alchemylibCurseFileId"))
    implementation(deobf("curse.maven:alchemistry-293425:$alchemistryCurseFileId"))
    implementation(deobf("curse.maven:create-new-age-905861:$createNewAgeCurseFileId"))
    compileOnly(deobf("curse.maven:emi-580555:$emiCurseFileId"))
    runtimeOnly(deobf("curse.maven:emi-580555:$emiCurseFileId"))
    compileOnly(deobf("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion"))
    compileOnly(deobf("mezz.jei:jei-$minecraftVersion-forge-api:$jeiVersion"))
    runtimeOnly(deobf("mezz.jei:jei-$minecraftVersion-forge:$jeiVersion"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.processResources {
    val props = mapOf(
        "minecraftVersion" to minecraftVersion,
        "forgeVersion" to forgeVersion,
        "kotlinForForgeVersion" to kotlinForForgeVersion,
        "createReleaseVersion" to createReleaseVersion,
        "createNewAgeVersion" to createNewAgeVersion,
        "chemlibVersion" to chemlibVersion,
        "alchemylibVersion" to alchemylibVersion,
        "alchemistryVersion" to alchemistryVersion,
        "emiVersion" to emiVersion,
        "jeiVersion" to jeiVersion,
        "modId" to modId,
        "modName" to modName,
        "modVersion" to modVersion,
        "modAuthors" to modAuthors,
        "modDescription" to modDescription,
        "modIssueTrackerUrl" to modIssueTrackerUrl,
        "modLicense" to modLicense
    )

    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.asFileTree.matching {
                include("com/gerald/latentchemlib/data/ChemicalTraits.class")
                include("com/gerald/latentchemlib/data/NumericCurve.class")
                include("com/gerald/latentchemlib/data/PresetCurve.class")
                include("com/gerald/latentchemlib/data/SchedulerProfile.class")
                include("com/gerald/latentchemlib/sim/ChemicalState.class")
                include("com/gerald/latentchemlib/sim/EmergentMath.class")
                include("com/gerald/latentchemlib/sim/SimulationBudget.class")
                include("com/gerald/latentchemlib/sim/SimulationBudgetLedger.class")
            }
        )
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs unit tests and, optionally, Forge GameTests with -PwithGameTests=true."
    dependsOn("test")
    dependsOn("jacocoTestCoverageVerification")

    if ((findProperty("withGameTests") as String?)?.toBoolean() == true) {
        dependsOn("runGameTestServer")
    }
}
