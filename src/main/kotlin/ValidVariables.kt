package validvariables

import antlr.gen.JavaLexer
import antlr.gen.JavaParser
import antlr.gen.JavaParserBaseListener
import com.google.gson.Gson
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.Configuration
import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import krangl.*
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.io.File
import java.nio.file.PathMatcher
import java.util.regex.Pattern
import com.jayway.jsonpath.Configuration.defaultConfiguration
import com.jayway.jsonpath.JsonPath
import java.lang.Integer
import java.util.*
import kotlin.system.exitProcess


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
            ctx?.getChild(0)?.getText()?.let { variableList.add(it) }
        }
    }
}

/*
 * Collects name statistics for an entire's file worth of (parseable) Java code
 */
fun collectNameStatistics(block : String) : NameStatistics {
    val javaParseTree = parseJava("""{
        $block
}""").block()
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
    val avgLength : Double = lengthSum.toDouble()/numTotal

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
data class NameStatistics(var numDescriptive : Int, var numTotal : Int, var avgLength : Double)

fun main() {
    val mongoClientURI = MongoClientURI(File("/Users/arjunvnair/Documents/credentials.txt").readText())
    val mongoClient = MongoClient(mongoClientURI)
    val db = mongoClient.getDB("Fall2019Clean")

    var people : DataFrame = emptyDataFrame() // "email", "major", "college", "gender", "priorCSExperience"
    if (!File("people.csv").exists()) {
        val peopleMongo = db.getCollection("people")
        val iterable = peopleMongo.find()
        val cursor = iterable.iterator()
        while (cursor.hasNext()) {
            val next = cursor.next()
            val document = Configuration.defaultConfiguration().jsonProvider().parse(next.toString())

            var email : String = "N/A"
            try {
                email = JsonPath.read(document, "$.email")
            }
            catch (e : Exception) {}

            var major : String = "N/A"
            try {
                major = JsonPath.read(document, "$.major")
            }
            catch (e : Exception) {}

            var college : String = "N/A"
            try {
                college = JsonPath.read(document, "$.college")
            }
            catch (e : Exception) {}

            var gender : String = "N/A"
            try {
                val gender_dict : Map<String, Any> = JsonPath.read(document, "$.survey.questions.gender.answer")
                gender = gender_dict.keys.toList()[0]
            }
            catch (e : Exception) {} // We give gender an N/A if it is not provided

            var priorCSExperience : String = "N/A" // None, HS Only, Uni Only, HS-Uni (N/A if not answered)
            try {
                val collegeExperience : Map<String, Any> = JsonPath.read(document, "$.survey.questions.collegePreparation.answer")
                val hasCollegeExperience = !collegeExperience.keys.contains("None")
                val highSchoolExperience : Map<String, Any> = JsonPath.read(document, "$.survey.questions.highSchoolPreparation.answer")
                val hasHighSchoolExperience = !highSchoolExperience.keys.contains("None")
                if (hasCollegeExperience && hasHighSchoolExperience) {
                    priorCSExperience = "HS and Uni"
                }
                else if (hasCollegeExperience) {
                    priorCSExperience = "Uni Only"
                }
                else if (hasHighSchoolExperience) {
                    priorCSExperience = "HS Only"
                }
                else {
                    priorCSExperience = "None"
                }
            }
            catch (e : Exception) {}
            people = dataFrameOf(people.rows + sequenceOf(mapOf(Pair("email", email), Pair("gender", gender), Pair("major", major), Pair("college", college), Pair("priorCSExperience", priorCSExperience))))
        }
        people.writeCSV(File("people.csv"))
    }
    else {
        people = DataFrame.readCSV(File("people.csv"))
    }

    var i = 0 // If you want to skip some number of submissions, replace this number with that
    var submissions : DataFrame = emptyDataFrame() // "email", "timestamp"
    if (!File("submissions.csv").exists()) {
        val submissionsMongo = db.getCollection("plSubmissions")
        val iterable = submissionsMongo.find().skip(i)
        val cursor = iterable.iterator()
        while (cursor.hasNext()) {
            val next = cursor.next()
            i++
            val document = Configuration.defaultConfiguration().jsonProvider().parse(next.toString())

            var email : String = "N/A"
            try {
                email = JsonPath.read(document, "$.email")
            }
            catch (e : Exception) {}

            var numDescriptiveVar = 0
            var numTotalVar = 0
            var avgVarLength = 0.0

            try {
                val encodedSource : String = JsonPath.read(document, "$.submitted_answer._files[0].contents")
                val decodedSource = String(Base64.getDecoder().decode(encodedSource))
                val nameStatistics = collectNameStatistics(decodedSource)
                numDescriptiveVar = nameStatistics.numDescriptive
                numTotalVar = nameStatistics.numTotal
                avgVarLength = nameStatistics.avgLength
            }
            catch (e : Exception) {
                continue // Not having this data makes the entire row useless; thus, move on if it is missing or uncompileable
            }

            var timestamp : String = "N/A"
            try {
                timestamp = next["date"].toString() // Date is stored a little differently from our string data
            }
            catch (e : Exception) {
                print(e)
            }

            var mode : String = "N/A"
            try {
                mode = JsonPath.read(document, "$.mode")
            }
            catch (e : Exception) {}
            submissions = dataFrameOf(submissions.rows + sequenceOf(mapOf(Pair("email", email), Pair("numDescriptiveVar", numDescriptiveVar), Pair("numTotalVar", numTotalVar), Pair("avgVarLength", avgVarLength), Pair("timestamp", timestamp), Pair("mode", mode))))
            if (submissions.nrow % 10000 == 0) {
                submissions.writeCSV(File("submissionse" + submissions.nrow.toString() + "num" + i.toString() + ".csv"))
            }
        }
        submissions.writeCSV(File("submissions.csv"))
    }
    else {
        submissions = DataFrame.readCSV(File("submissions.csv"))
    }

    if (!File("people_updated.csv").exists()) { // We need to add variable stats for each student
        var numDescriptiveVarForEmail = HashMap<String, Int?>()
        var numTotalVarForEmail = HashMap<String, Int?>()
        var totalVarLengthForEmail = HashMap<String, Int?>()
        for (submission in submissions.rows) {
            val email : String = submission["email"] as String // We're using this as an identifier for people
            if (people.filterByRow { it["email"] == email }.nrow == 0) {
                continue // If the person doesn't exist in our df, move on
            }

            val numDescriptiveVar : Int = submission["numDescriptiveVar"] as Int
            val numTotalVar : Int = submission["numTotalVar"] as Int
            val avgVarLength : Double = submission["avgVarLength"] as Double
            val totalVarLength : Int = (avgVarLength * numTotalVar.toDouble()).toInt()

            numDescriptiveVarForEmail[email] = numDescriptiveVarForEmail.getOrDefault(email, 0)?.plus(numDescriptiveVar)
            numTotalVarForEmail[email] = numTotalVarForEmail.getOrDefault(email, 0)?.plus(numTotalVar)
            totalVarLengthForEmail[email] = totalVarLengthForEmail.getOrDefault(email, 0)?.plus(totalVarLength)
        }
        people = people.addColumn("numDescriptiveVar") { it["email"].map<String> { numDescriptiveVarForEmail.getOrDefault(it, 0) } }
        people = people.addColumn("numTotalVar") { it["email"].map<String> { numTotalVarForEmail.getOrDefault(it, 0) } }
        people = people.addColumn("totalVarLength") { it["email"].map<String> { totalVarLengthForEmail.getOrDefault(it, 0) } }
        people = people.addColumn("avgVarLength") { it["email"].map<String> {
            numTotalVarForEmail.getOrDefault(it, 0)?.toDouble()?.let { it1 ->
                totalVarLengthForEmail.getOrDefault(it, 0)?.toDouble()
                    ?.div(it1)
            }
        } }

        people.writeCSV(File("people_updated.csv"))
    }
}
