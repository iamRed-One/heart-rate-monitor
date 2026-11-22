package com.example.myapplication.heart

import java.math.BigDecimal

/** Bounded RFC4180 reader for canonical CSV and the user's full Vigil heart-rate export. */
object HeartCsv {
    fun canonical(bytes: ByteArray): ByteArray {
        require(bytes.size<=5*1024*1024){"File exceeds 5 MiB."}
        val text=bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();val cell=StringBuilder();var quoted=false;var i=0
        fun field(){row.add(cell.toString().trim());cell.setLength(0)}
        fun line(){field();if(row.any{it.isNotEmpty()})rows.add(row);row=mutableListOf();require(rows.size<=10001){"Limit is 10,000 readings per file."}}
        while(i<text.length){val c=text[i];when {
            c=='"' -> if(quoted&&i+1<text.length&&text[i+1]=='"'){cell.append('"');i++}else quoted=!quoted
            c==','&&!quoted->field()
            c=='\n'&&!quoted->line()
            c=='\r'&&!quoted->{}
            else->cell.append(c)
        };i++}
        require(!quoted){"A quoted CSV field is not closed."};if(cell.isNotEmpty()||row.isNotEmpty())line()
        require(rows.size>=2){"Choose a CSV containing readings."}
        val header=rows.first();require(header.distinct().size==header.size){"CSV headers must be unique."}
        val direct=header.toSet()==setOf("timestamp","heart_rate")
        val native=header.containsAll(listOf("ts_utc","value","metric","unit","device_id"))
        require(direct||native){"Use timestamp,heart_rate or a Vigil heart-rate export."}
        val devices=mutableSetOf<String>();val result=StringBuilder("timestamp,heart_rate\n")
        for((index,record)in rows.drop(1).withIndex()){
            require(record.size==header.size){"Row ${index+2}: column count does not match the header."}
            val values=header.zip(record).toMap()
            if(native){require(values["metric"]=="HEART_RATE"&&values["unit"]=="bpm"){"Row ${index+2}: only HEART_RATE values in bpm are supported."};devices.add(values["device_id"].orEmpty())}
            val stamp=values[if(direct)"timestamp" else "ts_utc"].orEmpty()
            require(!stamp.contains(',')&&!stamp.contains('\n')&&stamp.isNotBlank()){"Row ${index+2}: invalid timestamp."}
            val bpm=try{BigDecimal(values[if(direct)"heart_rate" else "value"]).intValueExact()}catch(e:Exception){throw IllegalArgumentException("Row ${index+2}: heart rate must be a whole number.")}
            require(bpm in 1..300){"Row ${index+2}: heart rate must be between 1 and 300."}
            result.append(stamp).append(',').append(bpm).append('\n')
        }
        require(devices.size<=1){"Import one device per file. Keep the original export for its device and provenance fields."}
        return result.toString().toByteArray()
    }
}
