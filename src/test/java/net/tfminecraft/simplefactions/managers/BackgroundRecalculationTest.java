package net.tfminecraft.simplefactions.managers;

import static org.mockito.Mockito.*;

import java.util.logging.Logger;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackgroundRecalculationTest {
  private GuiTestFixture ui;
  private ProvinceManager live;
  private ProvinceManager.BackgroundFlood flood;

  @BeforeEach
  void setup() {
    ui = new GuiTestFixture();
    live = mock(ProvinceManager.class);
    flood = mock(ProvinceManager.BackgroundFlood.class);
    when(live.prepareBackgroundFlood()).thenReturn(flood);
    when(ui.plugin.getProvinceManager()).thenReturn(live);
    when(ui.plugin.isEnabled()).thenReturn(true);
    when(ui.plugin.getLogger()).thenReturn(mock(Logger.class));
  }

  @AfterEach
  void cleanup() {
    ui.close();
  }

  private void finish() {
    ui.runAsyncTasks();
    ui.runTasks();
  }

  @Test
  void aFinishedFloodIsInstalledWithoutRecalculatingOnTheServerThread() {
    when(live.adopt(flood)).thenReturn(true);
    BackgroundRecalculation.request(ui.plugin, live);
    verify(live, never()).recalculateIfNeeded();
    finish();
    verify(flood).run();
    verify(live).adopt(flood);
    verify(live, never()).recalculateIfNeeded();
    verify(live, never()).recalculate();
  }

  @Test
  void aRefusedInstallOrAFailedRunRecalculatesIfStillNeeded() {
    when(live.adopt(flood)).thenReturn(false);
    BackgroundRecalculation.request(ui.plugin, live);
    finish();
    verify(live).recalculateIfNeeded();

    clearInvocations(live);
    doThrow(new IllegalStateException("flood failed")).when(flood).run();
    BackgroundRecalculation.request(ui.plugin, live);
    finish();
    verify(live, never()).adopt(any());
    verify(live).recalculateIfNeeded();

    clearInvocations(live);
    doThrow(new StackOverflowError("deep flood")).when(flood).run();
    BackgroundRecalculation.request(ui.plugin, live);
    finish();
    verify(live, never()).adopt(any());
    verify(live).recalculateIfNeeded();
  }

  @Test
  void aCopyThatCannotBeMadeRecalculatesRightAway() {
    when(live.prepareBackgroundFlood()).thenThrow(new IllegalStateException("no copy"));
    BackgroundRecalculation.request(ui.plugin, live);
    verify(live).recalculateIfNeeded();
    finish();
    verify(live, never()).adopt(any());
  }

  @Test
  void aNewerRequestSupersedesTheOneStillRunning() {
    ProvinceManager.BackgroundFlood newer = mock(ProvinceManager.BackgroundFlood.class);
    when(live.prepareBackgroundFlood()).thenReturn(flood, newer);
    when(live.adopt(newer)).thenReturn(true);
    BackgroundRecalculation.request(ui.plugin, live);
    BackgroundRecalculation.request(ui.plugin, live);
    verify(flood).cancel();
    finish();
    verify(live, never()).adopt(flood);
    verify(live).adopt(newer);
    verify(live, never()).recalculateIfNeeded();
  }

  @Test
  void nothingIsInstalledAfterDisableOrIntoAReplacedManager() {
    when(ui.plugin.isEnabled()).thenReturn(false);
    BackgroundRecalculation.request(ui.plugin, live);
    finish();
    verify(live, never()).adopt(any());
    verify(live, never()).recalculateIfNeeded();

    when(ui.plugin.isEnabled()).thenReturn(true);
    when(ui.plugin.getProvinceManager()).thenReturn(mock(ProvinceManager.class));
    BackgroundRecalculation.request(ui.plugin, live);
    finish();
    verify(live, never()).adopt(any());
    verify(live, never()).recalculateIfNeeded();
  }
}
