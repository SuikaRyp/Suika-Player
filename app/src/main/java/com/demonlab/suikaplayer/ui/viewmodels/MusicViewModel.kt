package com.demonlab.suikaplayer.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.demonlab.suikaplayer.tools.Song
import com.demonlab.suikaplayer.tools.SettingsManager
import com.demonlab.suikaplayer.tools.MetadataManager
import com.demonlab.suikaplayer.tools.OnlineMusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val metadataManager = MetadataManager(application)
    private val onlineMusicProvider = OnlineMusicProvider(application)

    // The song library now comes entirely from the bundled online catalog
    // (assets/online_songs.json). It is shown the same way whether the
    // device is online or offline - local device storage is never scanned.
    // Actually streaming a song still requires an internet connection, but
    // the library list itself is always available since it ships inside
    // the app.
    var allSongs by mutableStateOf<List<Song>>(emptyList())
        private set

    val visuallyDeletedIds = androidx.compose.runtime.mutableStateListOf<Long>()

    val filteredSongs: List<Song>
        get() = allSongs.filter { it.id !in visuallyDeletedIds }

    var isLoading by mutableStateOf(false)
        private set

    fun loadSongs() {
        if (allSongs.isNotEmpty()) return
        viewModelScope.launch {
            isLoading = true
            allSongs = withContext(Dispatchers.IO) {
                onlineMusicProvider.getOnlineSongs()
            }
            isLoading = false
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            isLoading = true
            val refreshed = withContext(Dispatchers.IO) {
                onlineMusicProvider.getOnlineSongs(forceReload = true)
            }
            if (refreshed.isNotEmpty()) {
                allSongs = refreshed
            }
            isLoading = false
        }
    }

    fun updateMetadata(
        song: Song,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        coverUri: android.net.Uri?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val success = metadataManager.updateSongMetadata(
                songId = song.id,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                coverUri = coverUri?.toString()
            )
            if (success) {
                allSongs = allSongs.map {
                    if (it.id == song.id) it.copy(
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre ?: it.genre,
                        coverUrl = coverUri?.toString() ?: it.coverUrl
                    ) else it
                }
                onSuccess()
                syncSongsInternal()
            }
        }
    }

    fun restoreOriginalMetadata(song: Song, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val success = metadataManager.clearMetadataOverride(song.id)
            if (success) {
                syncSongsInternal()
                onSuccess()
            }
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val success = metadataManager.updateFavoriteStatus(song.id, !song.isFavorite)
            if (success) {
                allSongs = allSongs.map {
                    if (it.id == song.id) it.copy(isFavorite = !song.isFavorite) else it
                }
            }
        }
    }

    fun syncFavoriteStatusInMemory(songId: Long, isFavorite: Boolean) {
        allSongs = allSongs.map {
            if (it.id == songId) it.copy(isFavorite = isFavorite) else it
        }
    }

    // PLAYLISTS
    var playlists by mutableStateOf<List<com.demonlab.suikaplayer.data.Playlist>>(emptyList())
        private set

    var playlistMappings by mutableStateOf<List<com.demonlab.suikaplayer.data.PlaylistSong>>(emptyList())
        private set

    var topSongStats by mutableStateOf<List<com.demonlab.suikaplayer.data.PlaybackStats>>(emptyList())
        private set
    var topPlaylistStats by mutableStateOf<List<com.demonlab.suikaplayer.data.PlaybackStats>>(emptyList())
        private set
    var topArtistStats by mutableStateOf<List<com.demonlab.suikaplayer.data.PlaybackStats>>(emptyList())
        private set

    fun loadPlaylists() {
        viewModelScope.launch {
            val (newPlaylists, newMappings) = withContext(Dispatchers.IO) {
                val db = com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication())
                Pair(db.playlistDao().getAllPlaylists(), db.playlistDao().getAllPlaylistMappings())
            }
            playlists = newPlaylists
            playlistMappings = newMappings
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao().insertPlaylist(
                    com.demonlab.suikaplayer.data.Playlist(name = name)
                )
            }
            loadPlaylists()
        }
    }

    private suspend fun awaitLoadPlaylists() {
        val (newPlaylists, newMappings) = withContext(Dispatchers.IO) {
            val db = com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication())
            Pair(db.playlistDao().getAllPlaylists(), db.playlistDao().getAllPlaylistMappings())
        }
        playlists = newPlaylists
        playlistMappings = newMappings
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao().addSongToPlaylist(
                    com.demonlab.suikaplayer.data.PlaylistSong(playlistId, songId)
                )
            }
            awaitLoadPlaylists()
            onComplete?.invoke()
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val db = com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication())
                val playlistSongs = songIds.map { com.demonlab.suikaplayer.data.PlaylistSong(playlistId, it) }
                db.playlistDao().addSongsToPlaylist(playlistSongs)
            }
            awaitLoadPlaylists()
            onComplete?.invoke()
        }
    }
    fun removeSongsFromPlaylist(playlistId: Long, songIds: List<Long>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val db = com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication())
                db.playlistDao().removeSongsFromPlaylist(playlistId, songIds)
            }
            awaitLoadPlaylists()
            onComplete?.invoke()
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao().removeSongFromPlaylist(playlistId, songId)
            }
            awaitLoadPlaylists()
            onComplete?.invoke()
        }
    }

    fun getPlaylistsContainingSong(songId: Long, callback: (List<Long>) -> Unit) {
        viewModelScope.launch {
            // This is a bit inefficient but works for now: check all playlists
            val all = withContext(Dispatchers.IO) {
                com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao().getAllPlaylists()
            }
            val containingIds = mutableListOf<Long>()
            withContext(Dispatchers.IO) {
                val dao = com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao()
                for (playlist in all) {
                    val songIds = dao.getSongIdsForPlaylist(playlist.id)
                    if (songIds.contains(songId)) {
                        containingIds.add(playlist.id)
                    }
                }
            }
            callback(containingIds)
        }
    }

    fun getSongsForPlaylist(playlistId: Long, callback: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) {
                com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao().getSongIdsForPlaylist(playlistId)
            }
            callback(allSongs.filter { it.id in ids })
        }
    }

    fun getSongsForPlaylistSync(playlistId: Long): List<Song> {
        val songIds = playlistMappings.filter { it.playlistId == playlistId }.sortedBy { it.addedAt }.map { it.songId }
        val songMap = allSongs.associateBy { it.id }
        return songIds.mapNotNull { songMap[it] }
    }

    fun deletePlaylist(playlist: com.demonlab.suikaplayer.data.Playlist, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao().deletePlaylist(playlist)
            }
            loadPlaylists()
            onComplete?.invoke()
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val db = com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication())
                val playlist = db.playlistDao().getAllPlaylists().find { it.id == playlistId }
                if (playlist != null) {
                    db.playlistDao().updatePlaylist(playlist.copy(name = newName))
                }
            }
            loadPlaylists()
            onComplete?.invoke()
        }
    }

    fun getPlaylistPreviewCovers(playlistId: Long, callback: (List<String?>) -> Unit) {
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) {
                com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao().getSongIdsForPlaylist(playlistId)
            }
            val covers = allSongs.filter { it.id in ids.take(4) }.map { it.coverUrl ?: it.albumArtUri?.toString() }
            callback(covers)
        }
    }

    fun getPlaylistInfo(playlistId: Long, callback: (Int, Long) -> Unit) {
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) {
                com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication()).playlistDao().getSongIdsForPlaylist(playlistId)
            }
            val playlistSongs = allSongs.filter { it.id in ids }
            val count = playlistSongs.size
            val totalDuration = playlistSongs.sumOf { it.duration }
            callback(count, totalDuration)
        }
    }

    private fun observeStats() {
        viewModelScope.launch {
            val db = com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(getApplication())
            val dao = db.playbackStatsDao()
            
            launch {
                dao.getTopByCountFlow("SONG", 3).collect { topSongStats = it }
            }
            launch {
                dao.getTopByTimeFlow("PLAYLIST", 1).collect { topPlaylistStats = it }
            }
            launch {
                dao.getTopByTimeFlow("ARTIST", 1).collect { topArtistStats = it }
            }
        }
    }

    fun prepareDeleteSong(song: Song) {
        if (!visuallyDeletedIds.contains(song.id)) {
            visuallyDeletedIds.add(song.id)
        }
    }

    fun undoDeleteSong(song: Song) {
        visuallyDeletedIds.remove(song.id)
    }

    fun deleteSongPermanently(songId: Long, songData: String, songUri: android.net.Uri) {
        val context = getApplication<Application>()
        val settingsManager = SettingsManager.getInstance(context)
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                try {
                    var success = false

                    // 1. Try to delete via SAF if we have a folder URI
                    val folderUriString = settingsManager.musicFolderUri as String?
                    if (folderUriString != null) {
                        val folderUri = android.net.Uri.parse(folderUriString)
                        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                        if (tree != null && tree.canWrite()) {
                            val fileName = java.io.File(songData).name
                            val fileInSaf = tree.findFile(fileName)
                            if (fileInSaf?.delete() == true) success = true
                        }
                    }

                    // 2. Delete from MediaStore
                    val rows = context.contentResolver.delete(songUri, null, null)
                    if (rows > 0) success = true
                    
                    // 3. Fallback: direct file delete
                    val file = java.io.File(songData)
                    if (file.exists() && file.delete()) success = true
                    
                    // 4. Delete metadata overrides (only if deletion succeeded)
                    if (success) {
                        val db = com.demonlab.suikaplayer.data.MusicDatabase.getDatabase(context)
                        val override = db.songOverrideDao().getOverrideForSong(songId)
                        if (override != null) {
                            db.songOverrideDao().deleteOverride(override)
                        }
                    }
                    
                    success
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            
            if (deleted) {
                loadSongs()
            }
            visuallyDeletedIds.remove(songId)
        }
    }

    init {
        loadSongs()
        loadPlaylists()
        observeStats()
    }

    override fun onCleared() {
        super.onCleared()
    }
}


