package com.example.aerogcsclone.database

import androidx.room.TypeConverter
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.google.android.gms.maps.model.LatLng
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Type converters for Room database to handle complex data types
 */
class MissionTemplateTypeConverters {

    // Custom type adapter for MissionItemInt to handle serialization issues
    private val missionItemIntTypeAdapter = object : JsonSerializer<MissionItemInt>, JsonDeserializer<MissionItemInt> {
        override fun serialize(src: MissionItemInt, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val jsonObject = JsonObject()

            // Serialize basic properties
            jsonObject.addProperty("targetSystem", src.targetSystem.toInt())
            jsonObject.addProperty("targetComponent", src.targetComponent.toInt())
            jsonObject.addProperty("seq", src.seq.toInt())
            jsonObject.addProperty("current", src.current.toInt())
            jsonObject.addProperty("autocontinue", src.autocontinue.toInt())

            // Serialize frame and command enum values
            jsonObject.addProperty("frame", src.frame.value.toInt())
            jsonObject.addProperty("command", src.command.value.toInt())

            // Serialize parameters
            jsonObject.addProperty("param1", src.param1)
            jsonObject.addProperty("param2", src.param2)
            jsonObject.addProperty("param3", src.param3)
            jsonObject.addProperty("param4", src.param4)

            // Serialize position
            jsonObject.addProperty("x", src.x)
            jsonObject.addProperty("y", src.y)
            jsonObject.addProperty("z", src.z)

            return jsonObject
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MissionItemInt {
            val jsonObject = json.asJsonObject

            return MissionItemInt(
                targetSystem = jsonObject.get("targetSystem").asInt.toUByte(),
                targetComponent = jsonObject.get("targetComponent").asInt.toUByte(),
                seq = jsonObject.get("seq").asInt.toUShort(),
                frame = com.divpundir.mavlink.api.MavEnumValue.fromValue(jsonObject.get("frame").asInt.toUInt()),
                command = com.divpundir.mavlink.api.MavEnumValue.fromValue(jsonObject.get("command").asInt.toUInt()),
                current = jsonObject.get("current").asInt.toUByte(),
                autocontinue = jsonObject.get("autocontinue").asInt.toUByte(),
                param1 = jsonObject.get("param1").asFloat,
                param2 = jsonObject.get("param2").asFloat,
                param3 = jsonObject.get("param3").asFloat,
                param4 = jsonObject.get("param4").asFloat,
                x = jsonObject.get("x").asInt,
                y = jsonObject.get("y").asInt,
                z = jsonObject.get("z").asFloat
            )
        }
    }

    // Create Gson instance with custom type adapters
    private val gson = GsonBuilder()
        .registerTypeAdapter(MissionItemInt::class.java, missionItemIntTypeAdapter)
        .create()

    @TypeConverter
    fun fromMissionItemList(waypoints: List<MissionItemInt>): String {
        return gson.toJson(waypoints)
    }

    @TypeConverter
    fun toMissionItemList(waypointsString: String): List<MissionItemInt> {
        val listType = object : TypeToken<List<MissionItemInt>>() {}.type
        return gson.fromJson(waypointsString, listType)
    }

    @TypeConverter
    fun fromLatLngList(positions: List<LatLng>): String {
        return gson.toJson(positions)
    }

    @TypeConverter
    fun toLatLngList(positionsString: String): List<LatLng> {
        val listType = object : TypeToken<List<LatLng>>() {}.type
        return gson.fromJson(positionsString, listType)
    }

    @TypeConverter
    fun fromGridParameters(gridParameters: GridParameters?): String? {
        return gridParameters?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toGridParameters(gridParametersString: String?): GridParameters? {
        return gridParametersString?.let {
            gson.fromJson(it, GridParameters::class.java)
        }
    }
}
