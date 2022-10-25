import java.io.File
import java.util.Calendar
/*
 * weekday is as follow: 1 is sunday, 2 is monday... 7 is wednesday
 */
interface WeekdayEntry {
    fun match(day: Int):Boolean
}
/*
 * time is encoded as integer from 0 (0:00) to 1439 (23:59), resolution is minute, not second.
 */
interface TimeEntry {
    fun match(time: Int):Boolean
}

class Weekday (val day: Int): WeekdayEntry {
    override fun match(day: Int) = this.day==day
}
class WeekdayRange (val start: Int, val end: Int) : WeekdayEntry {
    override fun match(day: Int) = if (start<=end) start<=day && day<=end else day>=start || day<=end;
}
class MultipleDays (val days: List<WeekdayEntry>) : WeekdayEntry {
    override fun match(day: Int) = days.any {it.match(day)}
}

class TimeRange (val start: Int, val end: Int): TimeEntry {
    override fun match(time: Int) = start<=time && time<=end
}
class MultipleTimeRanges (val times: List<TimeEntry>): TimeEntry {
    override fun match(time:Int)=times.any {it.match(time)}
}

interface IMatchEntry {
    fun match(day: Int, time: Int):Boolean
}
class MatchEntry(val weekdayEntry: WeekdayEntry, val timeEntry: TimeEntry): IMatchEntry {
    override fun match(day: Int, time: Int)=weekdayEntry.match(day)&&timeEntry.match(time)
}
class MultipleMatchEntries(val matchEntries: List<IMatchEntry>): IMatchEntry {
    override fun match(day: Int, time: Int)=matchEntries.any {it.match(day,time)}
}

sealed interface ITodoEntry
data class TodoEntry(val what: String, val `when`: IMatchEntry): ITodoEntry
object InvalidTodoEntry: ITodoEntry

fun String.toTodoEntry(): ITodoEntry {
    val clauses=split(";")
    if (clauses.size<2) return InvalidTodoEntry
    val what=clauses[0].trim()
    val `when`=mutableListOf<IMatchEntry>()
    for (i in 1 until clauses.size) {
        val weekdayEntries=mutableListOf<WeekdayEntry>()
        var timeEntries=mutableListOf<TimeEntry>()
        clauses[i].trim(' ',',').map{it.lowercaseChar()}
            .joinToString(separator="")
            .split(" ",",").filter{it.length>0}.forEach {
                if (!it[0].isDigit()) {
                    val dayParser = {str: String ->
                        when (str) {
                            "t2","mon"->2
                            "t3","tue"->3
                            "t4","wed"->4
                            "t5","thu"->5
                            "t6","fri"->6
                            "t7","sat"->7
                            "cn","sun"->1
                            else->-1
                        }
                    }
                    it.split("-").filter{it.length>0}.let {
                        if (it.size==1) {
                            val day=dayParser(it[0])
                            if (day!=-1) {
                                weekdayEntries.add(Weekday(day))
                            }
                        }
                        else if (it.size==2) {
                            val startDay=dayParser(it[0])
                            val endDay=dayParser(it[1])
                            if (startDay!=-1 && endDay!=-1)
                                weekdayEntries.add(WeekdayRange(startDay, endDay))
                        }
                    }
                }
                else if (it[0].isDigit()) {
                    // Time must have a range
                    val times=it.split("-")
                    val timeParser = { str: String ->
                        var hour=-1; var minute=0
                        if (str.indexOf(":")!=-1) {
                            str.split(":").filter{it.length>0}.let {
                                if (it.size==2 && it[0][0].isDigit()) {
                                    hour=it[0].toInt()
                                    minute=it[1].toIntOrNull()?:0
                                }
                            }
                        }
                        else {
                            hour=str.toInt()
                        }
                        if (0<=hour && hour<=23 && 0<=minute && minute<=59)
                            hour*60+minute
                        else -1
                    }
                    if (times.size==2) {
                        val startTime=timeParser(times[0])
                        val endTime=timeParser(times[1])
                        if (startTime!=-1 && endTime!=-1)
                            timeEntries.add(TimeRange(startTime, endTime))
                    }
                }
        }
        if (weekdayEntries.size>0 && timeEntries.size>0)
            `when`.add(
                MatchEntry(
                    MultipleDays(weekdayEntries), 
                    MultipleTimeRanges(timeEntries)
                )
            )
    }
    if (`when`.size == 0) return InvalidTodoEntry
    return TodoEntry(what, MultipleMatchEntries(`when`))
}

fun whatToDo (db: List<TodoEntry>, currentDay: Int, currentTime: Int): List<TodoEntry>
    = db.filter {it.`when`.match(currentDay, currentTime)}

fun constructTodoDatabaseFromFile (file: File)
    = file.readLines(Charsets.UTF_8).map {it.toTodoEntry()}
        .filterNot {it is InvalidTodoEntry}
        .map {it as TodoEntry}

fun displayTodo (todo: List<TodoEntry>) {
    todo.forEach {println(it.what)}
}

fun debugWeekdayEntry (entry: WeekdayEntry): String {
    return if (entry is Weekday) entry.day.toString()
        else if (entry is WeekdayRange) "${entry.start}-${entry.end}"
        else if (entry is MultipleDays) {
            entry.days.joinToString {debugWeekdayEntry(it)}
        } else "UnknownWeekdayType"
}
fun debugTimeEntry (entry: TimeEntry): String {
    return if (entry is TimeRange) "${entry.start}-${entry.end}"
    else if (entry is MultipleTimeRanges) entry.times.joinToString {debugTimeEntry(it)}
    else "UnknownTimeType"
}
fun debugMatchEntry (entry: IMatchEntry): String {
    return if (entry is MatchEntry) "weekday: ${debugWeekdayEntry(entry.weekdayEntry)} time: ${debugTimeEntry(entry.timeEntry)}"
    else if (entry is MultipleMatchEntries) entry.matchEntries.joinToString {debugMatchEntry(it)}
    else "UnknownMatchEntryType"
}

fun main(args: Array<String>) {
    if (args.size==1) {
        val db = constructTodoDatabaseFromFile(File(args[0]))
        val (weekday, time) = Calendar.getInstance().run {
            val weekday = get(Calendar.DAY_OF_WEEK)
            val hour = get(Calendar.HOUR_OF_DAY)
            val minute = get(Calendar.MINUTE)
            weekday to hour*60+minute
        }
        val todo = whatToDo(db, weekday, time)
        displayTodo(todo)
    }
    else {
        println("Please provide only argument: path to data file")
    }
}