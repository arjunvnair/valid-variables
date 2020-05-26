import com.google.gson.Gson
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import java.io.File

val WORD_SPLIT_REGEX : Regex = Regex("(?=[1-9]+)|(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])") // Can be used to split the words in a variable name whether camel case, title case, or upper case (derived from: https://stackoverflow.com/questions/7593969/regex-to-split-camelcase-or-titlecase-advanced)
val GSON : Gson = Gson()
val DICTIONARY_PATH : String = "src/main/resources/words_dictionary.json"
val TYPE : Type = object : TypeToken<Map<String,Int>>() {}.type
val DICTIONARY : Map<String, Int> = GSON.fromJson(File(DICTIONARY_PATH).readText(), TYPE)

/**
 * Checks that each word in an identifier name, as
 * defined by Java naming conventions, is a word
 * in the dictionary
 *
 * Single character words are not considered valid,
 * even if they can be found in the dictionary; thus,
 * a variable named "i" is not considered descriptive
 *
 * Assumes that code follows checkstyle conventions
 *
 * @param idName the name of the identifier being checked
 * @return whether the identifier is descriptive
 */
fun isDescriptive(idName : String) : Boolean {
    if (idName.length == 1) {
        return false
    }

    var words : List<String> = idName.split(WORD_SPLIT_REGEX)
    println(words)
    for (word in words) {
        if (word.toLowerCase() !in DICTIONARY && word.toIntOrNull() == null) {
            return false // If word does not appear in the dictionary (and is not a number), then this id name is invalid
        }
    }
    return true
}

/**
 * Temporary testing suite until I get JUnit working
 */
fun main() {
    println(isDescriptive("camelCase"))
    println(isDescriptive("TitleCase"))
    println(isDescriptive("camelBleh"))
    println(isDescriptive("hueghsuhgriusCase"))
    println(isDescriptive("node3"))
    println(isDescriptive("properNounsLikeIllinois"))
}