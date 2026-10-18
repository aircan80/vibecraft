package dev.wowforever.app.music;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;

/** Exact Forever client assignments; day/night variants remain separate. */
public final class CityMusicCatalog {
  public record Track(long fileDataId,long entryId,long soundKitId,int frequency,double volume,int playerConditionId) {}
  public record City(long id,String name,long zoneMusicId,Map<String,List<Track>> variants) {
    public City {variants=Map.copyOf(variants);}
    public List<Track> tracks(LocalTime time) {
      return variants.getOrDefault(time.getHour()>=6&&time.getHour()<18?"day":"night",List.of()).stream()
          .filter(t->t.playerConditionId()==0&&t.frequency()>0).toList();
    }
  }
  private final String product,build;
  private final Map<Long,City> cities;
  private CityMusicCatalog(String product,String build,Map<Long,City> cities) {this.product=product;this.build=build;this.cities=Map.copyOf(cities);}
  public String product(){return product;}
  public String build(){return build;}
  public Optional<City> city(long id){return Optional.ofNullable(cities.get(id));}
  public static CityMusicCatalog bundled() {
    try(var input=CityMusicCatalog.class.getResourceAsStream("/data/city-music/catalog.json")) {
      if(input==null)throw new IOException("City music catalog unavailable");
      return read(input);
    }catch(IOException|RuntimeException failure){return new CityMusicCatalog("wow_classic_beta","",Map.of());}
  }
  static CityMusicCatalog read(InputStream input)throws IOException {
    var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
    var gson=new Gson();Map<Long,City> cities=new LinkedHashMap<>();
    for(var element:root.getAsJsonArray("cities")) {City city=gson.fromJson(element,City.class);cities.put(city.id(),city);}
    return new CityMusicCatalog(root.get("product").getAsString(),root.get("build").getAsString(),cities);
  }
}
