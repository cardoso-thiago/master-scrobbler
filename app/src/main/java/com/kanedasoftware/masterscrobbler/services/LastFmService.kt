package com.kanedasoftware.masterscrobbler.services

import com.kanedasoftware.masterscrobbler.model.*
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LastFmService {

    @GET("?method=artist.search&format=json")
    fun validateArtist(@Query("artist") artist: String, @Query("api_key") apiKey: String): Call<ArtistInfo>

    @GET("?method=track.search&format=json")
    fun validateTrackAndArtist(@Query("artist") artist: String, @Query("track") track: String,
                               @Query("api_key") apiKey: String): Call<TrackInfo>

    @GET("?method=track.search&format=json")
    fun validateTrack(@Query("track") track: String, @Query("api_key") apiKey: String): Call<TrackInfo>

    @GET("?method=track.getInfo&limit=1&format=json")
    fun fullTrackInfo(@Query("mbid") mbid: String, @Query("api_key") apiKey: String): Call<FullTrackInfo>


    @POST("?method=track.updateNowPlaying&format=json")
    fun updateNowPlaying(@Query("artist") artist: String, @Query("track") track: String,
                         @Query("api_key") apiKey: String, @Query("api_sig") sig: String,
                         @Query("sk") sessionKey: String): Call<UpdateNowPlayingInfo>

    @POST("?method=track.scrobble&format=json")
    fun scrobble(@Query("artist") artist: String, @Query("track") track: String,
                 @Query("album") album: String, @Query("api_key") apiKey: String,
                 @Query("api_sig") sig: String, @Query("sk") sessionKey: String,
                 @Query("timestamp") timestamp: String): Call<ScrobbleInfo>

    @GET("?method=user.getinfo&format=json")
    fun userInfo(@Query("username") username: String, @Query("api_key") apiKey: String): Call<UserInfo>

    @GET("?method=user.getTopAlbums&limit=9&format=json")
    fun topAlbums(@Query("username") username: String, @Query("period") period: String,
                  @Query("api_key") apiKey: String): Call<TopAlbumsInfo>

    @GET("?method=user.getTopArtists&limit=9&format=json")
    fun topArtists(@Query("username") username: String, @Query("period") period: String,
                  @Query("api_key") apiKey: String): Call<TopArtistsInfo>

    @GET("?method=user.getRecentTracks&extended=1&limit=20&format=json")
    fun recentTracks(@Query("username") username: String, @Query("api_key") apiKey: String): Call<RecentTracksInfo>
}