package dev.wowforever.app.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** An on/off setting a page reads when it is built and writes when the user changes it. */
public interface BooleanSetting {
  boolean isOn();

  void set(boolean on);

  /** A setting kept in memory only, {@code initial} at first. */
  static BooleanSetting inMemory(boolean initial) {
    return new BooleanSetting() {
      private boolean on = initial;

      @Override
      public boolean isOn() {
        return on;
      }

      @Override
      public void set(boolean on) {
        this.on = on;
      }
    };
  }

  /** A setting read from {@code get} and written to {@code set}. */
  static BooleanSetting of(BooleanSupplier get, Consumer<Boolean> set) {
    return new BooleanSetting() {
      @Override
      public boolean isOn() {
        return get.getAsBoolean();
      }

      @Override
      public void set(boolean on) {
        set.accept(on);
      }
    };
  }
}
