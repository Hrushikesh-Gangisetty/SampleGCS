package com.example.aerogcsclone.grid

import com.divpundir.mavlink.api.MavEnumValue
import com.divpundir.mavlink.definitions.common.*
import com.google.android.gms.maps.model.LatLng

/**
 * Converts grid survey waypoints to MAVLink mission items
 */
object GridMissionConverter {

    /**
     * Convert grid waypoints to MAVLink mission items
     * @param gridResult Grid survey result from GridGenerator
     * @param homePosition Home position for mission
     * @return List of MAVLink MissionItemInt objects
     */
    fun convertToMissionItems(
        gridResult: GridSurveyResult,
        homePosition: LatLng
    ): List<MissionItemInt> {
        val missionItems = mutableListOf<MissionItemInt>()

        // Sequence 0: Home position as NAV_WAYPOINT
        missionItems.add(
            MissionItemInt(
                targetSystem = 0u,
                targetComponent = 0u,
                seq = 0u,
                frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                command = MavEnumValue.of(MavCmd.NAV_WAYPOINT),
                current = 1u, // True for first item
                autocontinue = 1u,
                param1 = 0f,
                param2 = 0f,
                param3 = 0f,
                param4 = 0f,
                x = (homePosition.latitude * 1E7).toInt(),
                y = (homePosition.longitude * 1E7).toInt(),
                z = 10f // Default takeoff altitude
            )
        )

        // Sequence 1: Takeoff at home position
        missionItems.add(
            MissionItemInt(
                targetSystem = 0u,
                targetComponent = 0u,
                seq = 1u,
                frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                command = MavEnumValue.of(MavCmd.NAV_TAKEOFF),
                current = 0u,
                autocontinue = 1u,
                param1 = 0f,
                param2 = 0f,
                param3 = 0f,
                param4 = 0f,
                x = (homePosition.latitude * 1E7).toInt(),
                y = (homePosition.longitude * 1E7).toInt(),
                z = 15f // Takeoff altitude
            )
        )

        var sequenceNumber = 2
        var lastLineIndex = -1
        var isFirstWaypoint = true

        // Convert grid waypoints to mission items
        gridResult.waypoints.forEach { waypoint ->
            // For the first waypoint after takeoff, add speed command BEFORE the waypoint
            // This ensures we have NAV_WAYPOINT -> DO_CHANGE_SPEED -> NAV_WAYPOINT sequence
            if (isFirstWaypoint && waypoint.speed != null) {
                missionItems.add(
                    MissionItemInt(
                        targetSystem = 0u,
                        targetComponent = 0u,
                        seq = sequenceNumber.toUShort(),
                        frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                        command = MavEnumValue.of(MavCmd.DO_CHANGE_SPEED),
                        current = 0u,
                        autocontinue = 1u,
                        param1 = 0f, // Speed type: 0 = Airspeed, 1 = Ground Speed
                        param2 = waypoint.speed,
                        param3 = -1f, // Throttle (-1 = no change)
                        param4 = 0f,
                        x = 0,
                        y = 0,
                        z = 0f
                    )
                )
                sequenceNumber++
                isFirstWaypoint = false
            }
            // Add speed change command at start of each NEW line (but not the first waypoint)
            // This ensures we have NAV_WAYPOINT -> DO_CHANGE_SPEED -> NAV_WAYPOINT sequence
            else if (!isFirstWaypoint && waypoint.isLineStart && waypoint.speed != null && waypoint.lineIndex != lastLineIndex) {
                missionItems.add(
                    MissionItemInt(
                        targetSystem = 0u,
                        targetComponent = 0u,
                        seq = sequenceNumber.toUShort(),
                        frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                        command = MavEnumValue.of(MavCmd.DO_CHANGE_SPEED),
                        current = 0u,
                        autocontinue = 1u,
                        param1 = 0f, // Speed type: 0 = Airspeed, 1 = Ground Speed
                        param2 = waypoint.speed,
                        param3 = -1f, // Throttle (-1 = no change)
                        param4 = 0f,
                        x = 0,
                        y = 0,
                        z = 0f
                    )
                )
                sequenceNumber++
                lastLineIndex = waypoint.lineIndex
            }

            // Add the actual waypoint
            missionItems.add(
                MissionItemInt(
                    targetSystem = 0u,
                    targetComponent = 0u,
                    seq = sequenceNumber.toUShort(),
                    frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                    command = MavEnumValue.of(MavCmd.NAV_WAYPOINT),
                    current = 0u,
                    autocontinue = 1u,
                    param1 = 0f, // Hold time
                    param2 = 0f, // Acceptance radius
                    param3 = 0f, // Pass radius
                    param4 = 0f, // Yaw
                    x = (waypoint.position.latitude * 1E7).toInt(),
                    y = (waypoint.position.longitude * 1E7).toInt(),
                    z = waypoint.altitude
                )
            )
            sequenceNumber++

            if (isFirstWaypoint) {
                isFirstWaypoint = false
            }
        }

        // Add RTL (Return to Launch) at the end
        missionItems.add(
            MissionItemInt(
                targetSystem = 0u,
                targetComponent = 0u,
                seq = sequenceNumber.toUShort(),
                frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                command = MavEnumValue.of(MavCmd.NAV_RETURN_TO_LAUNCH),
                current = 0u,
                autocontinue = 1u,
                param1 = 0f,
                param2 = 0f,
                param3 = 0f,
                param4 = 0f,
                x = 0,
                y = 0,
                z = 0f
            )
        )

        return missionItems
    }

    /**
     * Convert single waypoint to mission item
     */
    private fun waypointToMissionItem(
        waypoint: GridWaypoint,
        sequence: Int
    ): MissionItemInt {
        return MissionItemInt(
            targetSystem = 0u,
            targetComponent = 0u,
            seq = sequence.toUShort(),
            frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
            command = MavEnumValue.of(MavCmd.NAV_WAYPOINT),
            current = 0u,
            autocontinue = 1u,
            param1 = 0f,
            param2 = 0f,
            param3 = 0f,
            param4 = 0f,
            x = (waypoint.position.latitude * 1E7).toInt(),
            y = (waypoint.position.longitude * 1E7).toInt(),
            z = waypoint.altitude
        )
    }

    /**
     * Estimate mission duration
     * @param gridResult Grid survey result
     * @param cruiseSpeed Average cruise speed in m/s
     * @return Estimated time in minutes
     */
    fun estimateMissionDuration(gridResult: GridSurveyResult, cruiseSpeed: Float = 10f): Double {
        val surveyTime = (gridResult.totalDistance / cruiseSpeed) / 60f // Convert to minutes
        val setupTime = 2f // Minutes for takeoff, positioning, etc.
        val rtlTime = 1f // Minutes for return to launch

        return surveyTime + setupTime + rtlTime
    }

    /**
     * Calculate total mission items count
     */
    fun calculateMissionItemCount(gridResult: GridSurveyResult): Int {
        var count = 2 // Home + Takeoff
        count += gridResult.waypoints.size // Survey waypoints

        // Count speed change commands (one per line)
        val speedCommands = gridResult.waypoints.count { it.isLineStart && it.speed != null }
        count += speedCommands

        count += 1 // RTL

        return count
    }
}
