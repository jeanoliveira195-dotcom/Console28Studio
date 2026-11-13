package com.jean.console28.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Offline playlist storage: MediaStore IDs only. We never alter the user's audio files. */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("console28_playlists_v1", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> = readIds(JSONArray(prefs.getString("favorites", "[]")))

    fun toggleFavorite(id: Long): Set<Long> {
        val ids = favorites().toMutableSet()
        if (!ids.add(id)) ids.remove(id)
        prefs.edit().putString("favorites", JSONArray(ids.toList()).toString()).apply()
        return ids
    }

    fun playlists(): Map<String, List<Long>> {
        val obj = JSONObject(prefs.getString("playlists", "{}"))
        return buildMap {
            for (name in obj.keys()) {
                put(name, readIds(obj.getJSONArray(name)).toList())
            }
        }
    }

    fun create(nameInput: String): Boolean {
        val name = nameInput.trim().take(48)
        if (name.isBlank() || name.equals("Todas", true) ||
            name.equals("Adicionadas", true) || name.equals("Favoritas", true)) return false
        val playlists = playlists()
        if (playlists.keys.any { it.equals(name, ignoreCase = true) }) return false
        save(playlists + (name to emptyList()))
        return true
    }

    fun delete(name: String) {
        save(playlists().filterKeys { it != name })
    }

    fun toggleTrack(name: String, id: Long) {
        val playlists = playlists().toMutableMap()
        val ids = playlists[name]?.toMutableList() ?: return
        if (!ids.remove(id)) ids.add(id)
        playlists[name] = ids
        save(playlists)
    }

    private fun save(items: Map<String, List<Long>>) {
        val obj = JSONObject()
        items.forEach { (name, ids) -> obj.put(name, JSONArray(ids)) }
        prefs.edit().putString("playlists", obj.toString()).apply()
    }

    private fun readIds(array: JSONArray): Set<Long> = buildSet {
        for (i in 0 until array.length()) {
            val id = array.optLong(i, -1)
            if (id >= 0) add(id)
        }
    }
}
