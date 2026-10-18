package dev.wowforever.app.sound;
import dev.wowforever.app.music.CityMusicService;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;
class UiSoundServiceTest {
  Preferences prefs;UiSoundService service;
  @BeforeEach void setup(){prefs=Preferences.userRoot().node("/wowforever-tests/sounds-"+System.nanoTime());}
  @AfterEach void cleanup()throws Exception{if(service!=null)service.close();prefs.removeNode();}
  @Test void coldClickNeverPlaysLateAndAcquisitionNeverRunsOnEdt()throws Exception{
    CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger plays=new AtomicInteger();
    service=new UiSoundService((p,b,id)->{assertFalse(SwingUtilities.isEventDispatchThread());entered.countDown();release.await();return Path.of("sound");},player(plays),prefs,System::nanoTime);
    SwingUtilities.invokeAndWait(()->{service.configure(UiSoundService.PRODUCT,UiSoundService.BUILD);service.play(UiSoundService.Cue.OPEN);});
    assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(0,plays.get());release.countDown();Thread.sleep(100);assertEquals(0,plays.get());
    service.play(UiSoundService.Cue.OPEN);await(()->plays.get()==1);
  }
  @Test void wrongBuildDoesNotAcquireOrPlay()throws Exception{
    AtomicInteger acquires=new AtomicInteger(),plays=new AtomicInteger();service=new UiSoundService((p,b,id)->{acquires.incrementAndGet();return Path.of("x");},player(plays),prefs,System::nanoTime);
    service.configure("wow_classic","1.15.0");service.play(UiSoundService.Cue.OPEN);Thread.sleep(50);assertEquals(0,acquires.get());assertEquals(0,plays.get());
  }
  @Test void debounceAndMuteSuppressFeedback()throws Exception{
    AtomicInteger acquires=new AtomicInteger(),plays=new AtomicInteger();AtomicLong now=new AtomicLong(1_000_000_000);
    service=new UiSoundService((p,b,id)->{acquires.incrementAndGet();return Path.of("x");},player(plays),prefs,now::get);
    service.configure(UiSoundService.PRODUCT,UiSoundService.BUILD);await(()->acquires.get()==5);Thread.sleep(30);
    service.play(UiSoundService.Cue.OPEN);await(()->plays.get()==1);service.play(UiSoundService.Cue.TAB);Thread.sleep(30);assertEquals(1,plays.get());
    now.addAndGet(150_000_000);service.muted(true);service.play(UiSoundService.Cue.CLOSE);Thread.sleep(30);assertEquals(1,plays.get());
  }
  @Test void settingsAreIndependentAndBounded(){service=new UiSoundService((p,b,id)->Path.of("x"),player(new AtomicInteger()),prefs,System::nanoTime);assertEquals(22,service.volume());service.volume(999);assertEquals(100,service.volume());service.volume(-4);assertEquals(0,service.volume());}
  static CityMusicService.Player player(AtomicInteger count){return new CityMusicService.Player(){public void play(Path p,BooleanSupplier valid,DoubleSupplier volume){assertFalse(SwingUtilities.isEventDispatchThread());if(valid.getAsBoolean())count.incrementAndGet();}public void stop(){}};}
  static void await(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(5);assertTrue(condition.getAsBoolean());}
}
