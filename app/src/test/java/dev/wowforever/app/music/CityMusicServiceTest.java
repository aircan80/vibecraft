package dev.wowforever.app.music;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.prefs.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

class CityMusicServiceTest {
  private String previousEnabled;
  @BeforeEach void enableMusicForControlledPlayer(){previousEnabled=System.getProperty("wowforever.music.enabled");System.setProperty("wowforever.music.enabled","true");}
  @AfterEach void restoreMusicSetting(){if(previousEnabled==null)System.clearProperty("wowforever.music.enabled");else System.setProperty("wowforever.music.enabled",previousEnabled);}

  static final class MemoryPreferences extends AbstractPreferences {
    final Map<String,String> values=new ConcurrentHashMap<>();
    MemoryPreferences(){super(null,"");}
    protected void putSpi(String key,String value){values.put(key,value);}
    protected String getSpi(String key){return values.get(key);}
    protected void removeSpi(String key){values.remove(key);}
    protected void removeNodeSpi(){}
    protected String[] keysSpi(){return values.keySet().toArray(String[]::new);}
    protected String[] childrenNamesSpi(){return new String[0];}
    protected AbstractPreferences childSpi(String name){throw new UnsupportedOperationException();}
    protected void syncSpi(){}
    protected void flushSpi(){}
  }
  static class FakePlayer implements CityMusicService.Player {
    final AtomicInteger plays=new AtomicInteger(),stops=new AtomicInteger();
    final CountDownLatch played=new CountDownLatch(1);
    volatile boolean playedOnEdt;
    public void play(Path file,BooleanSupplier current,DoubleSupplier volume)throws Exception {
      playedOnEdt=SwingUtilities.isEventDispatchThread();plays.incrementAndGet();played.countDown();
      while(current.getAsBoolean()&&!Thread.currentThread().isInterrupted())Thread.sleep(2);
    }
    public void stop(){stops.incrementAndGet();}
  }
  static void await(BooleanSupplier condition)throws Exception {
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
    while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(5);
    assertThat(condition.getAsBoolean()).isTrue();
  }

  @Test void nativeCatalogRetainsSixCitiesAndBothPhasesWithoutEraFallback() {
    var catalog=CityMusicCatalog.bundled();assertThat(catalog.build()).isEqualTo("1.60.1.70450");
    for(long id:new long[]{1497,1519,1537,1637,1638,1657}) {
      var city=catalog.city(id).orElseThrow();
      assertThat(city.tracks(LocalTime.NOON)).isNotEmpty();assertThat(city.tracks(LocalTime.MIDNIGHT)).isNotEmpty();
      assertThat(city.tracks(LocalTime.NOON)).allMatch(track->track.fileDataId()>0&&track.playerConditionId()==0);
    }
    assertThat(catalog.city(1497).orElseThrow().zoneMusicId()).isEqualTo(182);
    assertThat(catalog.city(1537).orElseThrow().zoneMusicId()).isEqualTo(232);
    assertThat(catalog.city(99999)).isEmpty();
  }

  @Test void navigationReturnsWhileDownloadIsBlockedAndLeavingPreventsStaleAudio()throws Exception {
    var requested=new CountDownLatch(1);var release=new CountDownLatch(1);var returned=new CountDownLatch(1);
    var player=new FakePlayer();var offEdt=new AtomicBoolean();
    try(var service=new CityMusicService(CityMusicCatalog.bundled(),(p,b,id)->{
      offEdt.set(!SwingUtilities.isEventDispatchThread());requested.countDown();release.await();returned.countDown();return Path.of("track");
    },player,new MemoryPreferences())) {
      SwingUtilities.invokeAndWait(()->service.enterCity(1519,"1.60.1.70450"));
      assertThat(requested.await(3,TimeUnit.SECONDS)).isTrue();
      SwingUtilities.invokeAndWait(service::leaveCity);release.countDown();assertThat(returned.await(3,TimeUnit.SECONDS)).isTrue();
      SwingUtilities.invokeAndWait(()->{});assertThat(service.state().cityId()).isZero();assertThat(player.plays.get()).isZero();assertThat(offEdt).isTrue();
    }finally{release.countDown();}
  }

  @Test void repeatedCityEntryDeduplicatesAssetAndMusicStopsOnLeave()throws Exception {
    var count=new AtomicInteger();var player=new FakePlayer();
    try(var service=new CityMusicService(CityMusicCatalog.bundled(),(p,b,id)->{count.incrementAndGet();return Path.of("track");},player,new MemoryPreferences())) {
      SwingUtilities.invokeAndWait(()->{service.enterCity(1519,"1.60.1.70450");service.enterCity(1519,"1.60.1.70450");});
      assertThat(player.played.await(3,TimeUnit.SECONDS)).isTrue();assertThat(count.get()).isEqualTo(1);assertThat(player.playedOnEdt).isFalse();
      SwingUtilities.invokeAndWait(service::leaveCity);await(()->player.stops.get()>=2);
    }
  }

  @Test void nextWarmTrackWaitsUntilPendingStopCompletes()throws Exception {
    var stopEntered=new CountDownLatch(1);var releaseStop=new CountDownLatch(1);var nextPlayed=new CountDownLatch(1);
    var player=new FakePlayer(){
      @Override public void stop(){if(stops.incrementAndGet()==2){stopEntered.countDown();try{releaseStop.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
      @Override public void play(Path path,BooleanSupplier current,DoubleSupplier volume)throws Exception {if(plays.get()==1)nextPlayed.countDown();super.play(path,current,volume);}
    };
    try(var service=new CityMusicService(CityMusicCatalog.bundled(),(p,b,id)->Path.of("warm-cache"),player,new MemoryPreferences())) {
      service.enterCity(1519,"1.60.1.70450");assertThat(player.played.await(3,TimeUnit.SECONDS)).isTrue();
      SwingUtilities.invokeAndWait(()->service.enterCity(1637,"1.60.1.70450"));
      assertThat(stopEntered.await(3,TimeUnit.SECONDS)).isTrue();assertThat(nextPlayed.await(100,TimeUnit.MILLISECONDS)).isFalse();
      releaseStop.countDown();assertThat(nextPlayed.await(3,TimeUnit.SECONDS)).isTrue();assertThat(service.state().cityId()).isEqualTo(1637);
    }finally{releaseStop.countDown();}
  }

  @Test void muteAndVolumePersistAndMutedCityDoesNotAcquireAssets()throws Exception {
    var preferences=new MemoryPreferences();var count=new AtomicInteger();preferences.putBoolean("muted",true);preferences.putInt("volume",22);
    try(var service=new CityMusicService(CityMusicCatalog.bundled(),(p,b,id)->{count.incrementAndGet();return Path.of("track");},new FakePlayer(),preferences)) {
      service.enterCity(1519,"1.60.1.70450");assertThat(service.state().status()).contains("muted");assertThat(count.get()).isZero();
      service.volume(67);await(()->preferences.getInt("volume",0)==67);assertThat(service.state().volume()).isEqualTo(67);
      service.muted(false);await(()->count.get()==1);await(()->!preferences.getBoolean("muted",true));
    }
  }

  @Test void wrongBuildNeverLoadsMusicAndListenersRunOnEdt()throws Exception {
    var player=new FakePlayer();var calls=new AtomicInteger();var notified=new CountDownLatch(1);var onEdt=new AtomicBoolean();
    try(var service=new CityMusicService(CityMusicCatalog.bundled(),(p,b,id)->{calls.incrementAndGet();return Path.of("track");},player,new MemoryPreferences())) {
      service.addListener(s->{onEdt.set(SwingUtilities.isEventDispatchThread());notified.countDown();});
      service.enterCity(1519,"1.15.9.70003");assertThat(notified.await(3,TimeUnit.SECONDS)).isTrue();
      assertThat(onEdt).isTrue();assertThat(service.state().status()).contains("unavailable");assertThat(calls.get()).isZero();
    }
  }
}
