import org.junit.Test
import validvariables.isDescriptive
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class TestValidVariables {
    @Test
    fun descriptiveCamelCase() {
        assertTrue(isDescriptive("camelCase")) // Valid variable and method names should be descriptive
    }

    @Test
    fun descriptiveTitleCase() {
        assertTrue(isDescriptive("TitleCase")) // Valid class names should still be descriptive
    }

    @Test
    fun descriptiveAbbreviated() {
        assertTrue(isDescriptive("num")) // The dictionary has abbreviations as well!
        assertTrue(isDescriptive("msg"))
        assertTrue(isDescriptive("temp"))
        assertTrue(isDescriptive("tmp"))
        assertTrue(isDescriptive("min"))
        assertTrue(isDescriptive("max"))
    }

    @Test
    fun descriptiveNumbered() {
        assertTrue(isDescriptive("node3")) // Sometimes multiple numbered variables will be used: node, node2, node3, etc.
    }

    @Test
    fun descriptiveNumberedMultiDigit() {
        assertTrue(isDescriptive("num11")) // Does it appropriately handle numbers with multiple digits?
    }

    @Test
    fun properNouns() {
        assertTrue(isDescriptive("properNounsLikeIllinois")) // Make sure that words that are naturally capitalized will still be found
                                                                    // This works because all words in the dictionary JSON are lowercase
    }

    @Test
    fun notDescriptiveCamelCase() {
        assertFalse(isDescriptive("camelBleh")) // One invalid word should invalidate the whole name
    }

    @Test
    fun notDescriptiveCamelCase2() {
        assertFalse(isDescriptive("hueghsuhgriusCase")) // This applies regardless of whether the word is at the start or the end
    }

    @Test
    fun notDescriptiveChar() {
        assertFalse(isDescriptive("i")) // A single character is not considered descriptive (though this is still perfectly good style to use in many cases and will be ignored by the analyzer in a for control)
    }
}



