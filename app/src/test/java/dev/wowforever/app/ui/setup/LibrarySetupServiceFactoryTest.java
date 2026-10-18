package dev.wowforever.app.ui.setup;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LibrarySetupServiceFactoryTest {
  @Test void creationOnEdtFreezesActiveProtectionWithoutCreatingStorage()throws Exception{
    Path root=Files.createTempDirectory("vibecraft-factory-");try{
      Path storage=root.resolve("not-created");var selectedRoots=new ArrayList<>(List.of(root.resolve("read-only-wow")));Path active=root.resolve("current.sqlite");
      var environment=new LibrarySetupServiceFactory.Environment(storage,selectedRoots,Optional.of(active));selectedRoots.clear();
      var captured=new AtomicReference<LibrarySetupServiceFactory.Environment>();
      SwingUtilities.invokeAndWait(()->{
        var service=LibrarySetupServiceFactory.create(environment,value->{captured.set(value);return new DefaultLibrarySetupService(value.storage(),value.protectedGameRoots(),value.activeDatabase());});
        assertThat(service).isInstanceOf(DefaultLibrarySetupService.class);
      });
      assertThat(captured.get().storage()).isEqualTo(storage);assertThat(captured.get().protectedGameRoots()).containsExactly(root.resolve("read-only-wow"));assertThat(captured.get().activeDatabase()).contains(active);assertThat(Files.exists(storage)).isFalse();
    }finally{Files.delete(root);}
  }
  @Test void defaultFactoryCapturesOneActivePermissionContextWithoutStartingWork()throws Exception{
    var previous=dev.wowforever.app.assets.AssetDownloadPermissions.snapshot();
    Path active=Path.of("current-managed.sqlite").toAbsolutePath(),game=Path.of("read-only-game").toAbsolutePath();
    var consent=new dev.wowforever.app.assets.AssetDownloadPermissions.Consent(dev.wowforever.app.assets.AssetDownloadPermissions.Mode.MANAGED,true,true,List.of(game));
    try{
      dev.wowforever.app.assets.AssetDownloadPermissions.configureManaged(active,consent);
      SwingUtilities.invokeAndWait(()->{
        var context=LibrarySetupServiceFactory.snapshotEnvironment();assertThat(context.storage()).isEqualTo(dev.wowforever.app.db.UserDataDir.resolve());assertThat(context.activeDatabase()).contains(active);assertThat(context.protectedGameRoots()).containsExactly(game);
        assertThat(LibrarySetupServiceFactory.create()).isInstanceOf(DefaultLibrarySetupService.class);
      });
    }finally{
      if(previous.activeDatabase().isEmpty())dev.wowforever.app.assets.AssetDownloadPermissions.resetForCatalogResolution();
      else if(previous.consent().mode()==dev.wowforever.app.assets.AssetDownloadPermissions.Mode.LEGACY)dev.wowforever.app.assets.AssetDownloadPermissions.configureLegacy(previous.activeDatabase().orElseThrow());
      else dev.wowforever.app.assets.AssetDownloadPermissions.configureManaged(previous.activeDatabase().orElseThrow(),previous.consent());
    }
  }
  @Test void connectedAndUnavailableHotfixCopyRemainDistinctWithoutRunningAnyJob()throws Exception{
    var prefs=java.util.prefs.Preferences.userRoot().node("vibecraft-tests/factory-copy-"+UUID.randomUUID());
    try{
      prefs.putBoolean("forever.source.wago",true);prefs.putBoolean("forever.source.raidbots-hotfix",true);
      SwingUtilities.invokeAndWait(()->{
        var absent=new LibrarySetupPanel(prefs,path->{},null);advance(absent,4);
        assertThat(text(absent)).contains("still need explicit pinning and validation");assertThat(find(absent,"setup-build-library",javax.swing.JButton.class).isEnabled()).isFalse();absent.close();
        LibrarySetupService noWork=new LibrarySetupService(){
          public Capabilities capabilities(){throw new AssertionError("Construction must not inspect capabilities");}
          public Review review(LibrarySetupDraft draft,Control control){throw new AssertionError("No review requested");}
          public Result build(Review review,Mode mode,Control control){throw new AssertionError("No build requested");}
        };
        var connected=new LibrarySetupPanel(prefs,path->{},null,noWork);advance(connected,4);
        assertThat(text(connected)).contains("Requirements review must check the current snapshot and definition pins").doesNotContain("still need explicit pinning");
        assertThat(find(connected,"setup-build-library",javax.swing.JButton.class).isEnabled()).isFalse();
        find(connected,"setup-navigation",javax.swing.JTabbedPane.class).setSelectedIndex(1);
        assertThat(text(connected)).contains("pinned dependencies").doesNotContain("Setup integration pending","Setup integration is pending");connected.close();
      });
    }finally{prefs.removeNode();}
  }
  private static void advance(java.awt.Container root,int count){for(int i=0;i<count;i++)find(root,"setup-next",javax.swing.JButton.class).doClick();}
  private static <T extends java.awt.Component>T find(java.awt.Container root,String name,Class<T> type){for(java.awt.Component child:root.getComponents()){if(type.isInstance(child)&&name.equals(child.getName()))return type.cast(child);if(child instanceof java.awt.Container nested){try{return find(nested,name,type);}catch(AssertionError ignored){}}}throw new AssertionError(name);}
  private static String text(java.awt.Container root){var out=new StringBuilder();for(java.awt.Component child:root.getComponents()){if(child instanceof javax.swing.JTextArea area)out.append(area.getText());if(child instanceof java.awt.Container nested)out.append(text(nested));}return out.toString();}

}
