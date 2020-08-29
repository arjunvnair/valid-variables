package validvariables

import antlr.gen.JavaLexer
import antlr.gen.JavaParser
import antlr.gen.JavaParserBaseListener
import com.google.gson.Gson
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.tree.ParseTreeWalker
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
    for (word in words) {
        if (word.toLowerCase() !in DICTIONARY && word.toIntOrNull() == null) {
            return false // If word does not appear in the dictionary (and is not a number), then this id name is invalid
        }
    }
    return true
}

/**
 * Listener that scrapes variable names
 */
class VariableListener() : JavaParserBaseListener() {
    var variableList : MutableList<String> = mutableListOf()

    private var inForControl : Boolean = false; // Whether the variable analyzer is currently walking through a for control statement

    override fun enterForControl(ctx: JavaParser.ForControlContext?) {
        inForControl = true; // We have entered a for control
    }

    override fun exitForControl(ctx: JavaParser.ForControlContext?) {
        inForControl = false; // We have left the for control
    }

    override fun enterVariableDeclarator(ctx: JavaParser.VariableDeclaratorContext?) {
        if(!inForControl) { // We only add a variable if it is not inside a for control
            ctx?.getChild(3)?.getText()?.let { variableList.add(it) }
        }
    }
}

/*
 * Collects name statistics for an entire's file worth of (parseable) Java code
 */
fun collectNameStatistics(unit : String) : NameStatistics {
    val javaParseTree = parseJava(unit).compilationUnit()
    val listener = VariableListener()
    val walker = ParseTreeWalker()
    walker.walk(listener, javaParseTree) // Collect naming statistics
    val variableList = listener.variableList // Extract the variables from it (excluding for control)

    var lengthSum = 0 // Sum of all variable lengths
    var numDescriptive = 0 // Count of all descriptive variables
    var numTotal = variableList.size // Count of all variables

    for (variable in variableList) {
        lengthSum += variable.length
        if (isDescriptive(variable)) {
            numDescriptive++;
        }
    }
    val avgLength = lengthSum/numTotal

    return NameStatistics(numDescriptive, numTotal, avgLength)
}

/**
 * Parses Java code.
 *
 * @param source - The Java source code to be parsed.
 * @return Returns a parser.
 */
private fun parseJava(source: String): JavaParser {
    val javaErrorListener = JavaExceptionListener()
    val charStream = CharStreams.fromString(source)
    val javaLexer = JavaLexer(charStream)
    javaLexer.removeErrorListeners()
    javaLexer.addErrorListener(javaErrorListener)

    val tokenStream = CommonTokenStream(javaLexer)
    return JavaParser(tokenStream).also {
        it.removeErrorListeners()
        it.addErrorListener(javaErrorListener)
    }
}

/**
 * A class that creates a Java Listener.
 */
private class JavaExceptionListener : BaseErrorListener() {
    /**
     * Detects Java syntax errors.
     *
     * @param recognizer -
     * @param offendingSymbol - The illegal symbol.
     * @param line - The line [offendingSymbol] was on.
     * @param charPositionInLine - The character position of [offendingSymbol] within the [line].
     * @param msg - Error message to display.
     * @param e -
     * @throws [JavaParseException]
     */
    @Override
    override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
        throw JavaParseException(line, charPositionInLine, msg)
    }
}

/**
 * A class that holds information about what went wrong while parsing Java code.
 */
class JavaParseException(val line: Int, val column: Int, message: String) : Exception(message)

/*
 * Statistics on variable names in a piece of code (excludes for control variables)
 *
 * @param numDescriptive the number of descriptive variables
 * @param numTotal the number of variables in total
 * @param avgLength the average length of a variable
 */
data class NameStatistics(var numDescriptive : Int, var numTotal : Int, var avgLength : Int)