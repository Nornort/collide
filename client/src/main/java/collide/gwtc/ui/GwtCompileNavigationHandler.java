package collide.gwtc.ui;

import xapi.collect.X_Collect;
import xapi.collect.api.IntTo;
import xapi.fu.Lazy;
import xapi.gwtc.api.GwtManifest;
import xapi.time.impl.RunOnce;
import xapi.util.api.SuccessHandler;
import collide.gwtc.ui.GwtCompilerShell.Resources;
import collide.gwtc.ui.GwtCompilerShell.View;

import com.google.collide.client.AppContext;
import com.google.collide.client.communication.FrontendApi.ApiCallback;
import com.google.collide.client.history.Place;
import com.google.collide.client.history.PlaceNavigationHandler;
import com.google.collide.client.status.StatusMessage;
import com.google.collide.client.ui.panel.MultiPanel;
import com.google.collide.client.ui.panel.PanelContent;
import com.google.collide.client.ui.panel.PanelModel;
import com.google.collide.dto.CompileResponse;
import com.google.collide.dto.GwtRecompile;
import com.google.collide.dto.GwtSettings;
import com.google.collide.dto.ServerError.FailureReason;
import com.google.collide.dto.client.DtoClientImpls.GwtCompileImpl;
import com.google.gwt.core.client.Duration;
import com.google.gwt.core.shared.GWT;

import elemental.util.ArrayOfString;
import elemental.util.Collections;

public class GwtCompileNavigationHandler
extends PlaceNavigationHandler<GwtCompilePlace.NavigationEvent>
implements GwtCompilerService
{

  private final AppContext context;
  private final MultiPanel<? extends PanelModel,?> contentArea;
  private final Place currentPlace;
  private final GwtManifest model;
  private final Lazy<GwtCompilerShell> gwtContainer;
  private final Lazy<Resources> gwtResources;

  public GwtCompileNavigationHandler(AppContext context, MultiPanel<?,?> masterPanel, Place currentPlace) {
    this.context = context;
    this.contentArea = masterPanel;
    this.currentPlace = currentPlace;
    this.model = GWT.create(GwtManifest.class);
    //TODO load defaults from local storage

    //create our view lazily
    this.gwtContainer = Lazy.deferred1(this::initializeView);
    this.gwtResources = Lazy.deferred1(()->{
        Resources res = GWT.create(Resources.class);
        res.gwtCompilerCss().ensureInjected();
        res.gwtLogCss().ensureInjected();
        res.gwtClasspathCss().ensureInjected();
        res.gwtModuleCss().ensureInjected();
        return res;
    });
  }

  protected GwtCompilerShell initializeView() {
    View view = new GwtCompilerShell.View(context,model,gwtResources.out1());
    return GwtCompilerShell.create(view, context);
  }

  @Override
  public void cleanup() {
    contentArea.getToolBar().show();
  }

  double nextRefresh;
  RunOnce once = new RunOnce();
  @Override
  protected void enterPlace(GwtCompilePlace.NavigationEvent navigationEvent) {
    boolean run = once.shouldRun(false);
    if (run) {
      contentArea.setHeaderVisibility(false);
      contentArea.clearNavigator();
    }else {
      model.setModuleName(navigationEvent.getModule());
      IntTo<String> all = X_Collect.newList(String.class);
      for (String src : navigationEvent.getSourceDirectory().asIterable()) {
        all.push(src);
      }
      model.setSources(all);

      all = X_Collect.newList(String.class);
      for (String src : navigationEvent.getLibsDirectory().asIterable()) {
        all.push(src);
      }
      model.setDependencies(all);

      gwtContainer.out1().setPlace(navigationEvent);
      return;
    }



    PanelContent panelContent = gwtContainer.out1();
    contentArea.setContent(panelContent,
      contentArea.newBuilder().setCollapseIcon(true).setClearNavigator(true).build());
    contentArea.getToolBar().hide();
    final StatusMessage message =
        new StatusMessage(context.getStatusManager(), StatusMessage.MessageType.LOADING,
            "Checking Compiler...");

    message.fireDelayed(100);
    double now = Duration.currentTimeMillis();
    if (now > nextRefresh) {
      nextRefresh = now + 10000;
    context.getFrontendApi().GWT_SETTINGS.request(
        new ApiCallback<GwtSettings>() {

          @Override
          public void onFail(FailureReason reason) {
            new StatusMessage(context.getStatusManager(), StatusMessage.MessageType.ERROR,
                "Retrieving gwt settings failed in 3 attempts.  Try again later.").fire();
          }

          @Override
          public void onMessageReceived(GwtSettings response) {
            gwtContainer.out1().showResults(response);
            message.expire(1);
          }
        });
    }
  }

  public Place getCurrentPlace() {
    return currentPlace;
  }

  public GwtCompileImpl getValue() {
    return gwtContainer.out1().getValue();
  }

  @Override
  public void compile(GwtRecompile module,
      SuccessHandler<CompileResponse> response) {
    // We lazy-load the gwt shell, and just defer to it.
    gwtContainer.out1().compile(module, response);
  }

  @Override
  public void recompile(String module, SuccessHandler<CompileResponse> response) {
    gwtContainer.out1().recompile(module, response);
  }

  @Override
  public void kill(String module) {
    gwtContainer.out1().kill(module);
  }

  @Override
  public GwtCompilerShell getShell() {
    return gwtContainer.out1();
  }

}
