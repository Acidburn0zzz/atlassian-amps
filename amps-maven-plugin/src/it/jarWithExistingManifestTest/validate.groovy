import java.util.jar.Manifest
import net.lingala.zip4j.core.ZipFile

final def testJar = new File("$basedir/target/testjar.jar")
assert testJar.exists(), "Test jar should exist at $testJar.absolutePath"

// unzip the manifest
final ZipFile testJarCompressed = new ZipFile("$basedir/target/testjar.jar");
testJarCompressed.extractAll("$basedir/target")

final def manifest = new File("$basedir/target/META-INF/MANIFEST.MF")
assert manifest.exists()

// checking that the jarred manifest is the same as the generated one
final Manifest extracted = manifest.withInputStream { new Manifest(it) }
final Manifest existing = new File("$basedir/target/classes/META-INF/MANIFEST.MF").withInputStream { new Manifest(it) }

extracted.mainAttributes.each { key, value ->
    final Object existingValue = existing.mainAttributes.get(key)
    assert value?.equals(existingValue), "Manifest entries should be equal, $key : $value | $existingValue"
    existing.mainAttributes.remove(key)
}
assert existing.mainAttributes.isEmpty(), "There shouldn't be any more entries"