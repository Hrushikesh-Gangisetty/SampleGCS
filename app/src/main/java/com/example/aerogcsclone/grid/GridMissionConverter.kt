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
     * @param holdNosePosition If true, adds MAV_CMD_CONDITION_YAW to maintain yaw throughout mission
     * @param initialYaw Initial yaw angle in degrees (0-360, 0=North, 90=East)
     * @param fcuSystemId Target FCU system ID (defaults to 0 for compatibility)
     * @param fcuComponentId Target FCU component ID (defaults to 0 for compatibility)
     * @return List of MAVLink MissionItemInt objects
     */
    fun convertToMissionItems(
        gridResult: GridSurveyResult,
        homePosition: LatLng,
        holdNosePosition: Boolean = false,
        initialYaw: Float = 0f,
        fcuSystemId: UByte = 0u,
        fcuComponentId: UByte = 0u
    ): List<MissionItemInt> {
        val missionItems = mutableListOf<MissionItemInt>()

        // CRITICAL FIX: Sequence 0 = HOME position (NAV_WAYPOINT with current=1, z=0f)
        missionItems.add(
            MissionItemInt(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                seq = 0u,
                frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                command = MavEnumValue.of(MavCmd.NAV_WAYPOINT),
                current = 1u, // MUST be 1 for first item
                autocontinue = 1u,
                param1 = 0f, // Hold time
                param2 = 3f, // Acceptance radius in meters (CRITICAL: must be >0)
                param3 = 0f, // Pass through
                param4 = 0f, // Yaw
                x = (homePosition.latitude * 1E7).toInt(),
                y = (homePosition.longitude * 1E7).toInt(),
                z = 0f // CRITICAL: HOME altitude must be 0 (relative)
            )
        )

        // Sequence 1: Takeoff at home position to survey altitude
        val takeoffAltitude = gridResult.waypoints.firstOrNull()?.altitude ?: 15f
        missionItems.add(
            MissionItemInt(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                seq = 1u,
                frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                command = MavEnumValue.of(MavCmd.NAV_TAKEOFF),
                current = 0u,
                autocontinue = 1u,
                param1 = 0f, // Minimum pitch
                param2 = 0f,
                param3 = 0f,
                param4 = 0f, // Yaw
                x = (homePosition.latitude * 1E7).toInt(),
                y = (homePosition.longitude * 1E7).toInt(),
                z = takeoffAltitude // Takeoff to survey altitude
            )
        )

        var sequenceNumber = 2

        // Add CONDITION_YAW command right after takeoff if holdNosePosition is enabled
        // This sets the yaw and maintains it throughout the mission
        if (holdNosePosition) {
            missionItems.add(
                MissionItemInt(
                    targetSystem = fcuSystemId,
                    targetComponent = fcuComponentId,
                    seq = sequenceNumber.toUShort(),
                    frame = MavEnumValue.of(MavFrame.MISSION),
                    command = MavEnumValue.of(MavCmd.CONDITION_YAW),
                    current = 0u,
                    autocontinue = 1u,
                    param1 = initialYaw, // Target yaw angle in degrees (0-360)
                    param2 = 0f, // Yaw speed (0 = maximum)
                    param3 = 1f, // Direction: 1 = clockwise, -1 = counter-clockwise
                    param4 = 0f, // 0 = absolute angle, 1 = relative angle
                    x = 0,
                    y = 0,
                    z = 0f
                )
            )
            sequenceNumber++
        }

        var lastLineIndex = -1
        var isFirstWaypoint = true

        // Convert grid waypoints to mission items (starting from seq=2 or seq=3 if holdNosePosition)
        gridResult.waypoints.forEach { waypoint ->
            // For the first waypoint after takeoff, add speed command BEFORE the waypoint
            // This ensures we have NAV_WAYPOINT -> DO_CHANGE_SPEED -> NAV_WAYPOINT sequence
            if (isFirstWaypoint && waypoint.speed != null) {
                missionItems.add(
                    MissionItemInt(
                        targetSystem = fcuSystemId,
                        targetComponent = fcuComponentId,
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
                        targetSystem = fcuSystemId,
                        targetComponent = fcuComponentId,
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
                    targetSystem = fcuSystemId,
                    targetComponent = fcuComponentId,
                    seq = sequenceNumber.toUShort(),
                    frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                    command = MavEnumValue.of(MavCmd.NAV_WAYPOINT),
                    current = 0u,
                    autocontinue = 1u,
                    param1 = 0f, // Hold time (0 = no hold)
                    param2 = 3f, // Acceptance radius in meters (2-5m recommended)
                    param3 = 0f, // Pass radius (0 = pass through waypoint)
                    param4 = 0f, // Yaw angle (0 = no change, or calculate from next waypoint)
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
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
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
            param1 = 0f, // Hold time (0 = no hold)
            param2 = 3f, // Acceptance radius in meters (2-5m recommended)
            param3 = 0f, // Pass radius (0 = pass through waypoint)
            param4 = 0f, // Yaw angle (0 = no change)
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
