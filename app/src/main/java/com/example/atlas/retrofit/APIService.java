package com.example.atlas.retrofit;




import com.example.atlas.retrofit.response.Hasil;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface APIService {

    @GET("api/place/nearbysearch/json")
    Call<Hasil> getHasil(
            @Query(value = "location", encoded = true) String location,
            @Query("radius") String radius,
            @Query("type") String type,
            @Query("key") String key);
}