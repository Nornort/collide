package collide.gwtc.ui;

import com.google.collide.client.history.Place;
import com.google.collide.client.history.PlaceConstants;
import com.google.collide.client.history.PlaceNavigationEvent;
import com.google.collide.client.workspace.WorkspacePlace;
import com.google.collide.dto.GwtRecompile;
import com.google.collide.dto.client.DtoClientImpls.GwtRecompileImpl;
import com.google.collide.json.client.JsoArray;
import com.google.collide.json.client.JsoStringMap;
import com.google.collide.json.shared.JsonStringMap;

import elemental.client.Browser;
import elemental.dom.Node;
import elemental.dom.NodeList;
import elemental.dom.TimeoutHandler;
import xapi.fu.Lazy;

public class GwtCompilePlace extends Place{

  public class NavigationEvent extends PlaceNavigationEvent<GwtCompilePlace> {
    public static final String MODULE_KEY = "m";
    public static final String SRC_KEY = "s";
    public static final String DEPS_KEY = "d";

    private final String module;
    private final JsoArray<String> srcDir;
    private final JsoArray<String> depsDir;
    private final boolean recompile;

    private NavigationEvent(GwtRecompile module) {
      super(GwtCompilePlace.this);
      this.module = module.getModule();
      this.recompile = module.isRecompile();
      if (module.getSources() == null) {
        this.srcDir = JsoArray.create();
        this.depsDir = JsoArray.create();
      } else {
        this.srcDir = JsoArray.from(module.getSources());
        this.depsDir = JsoArray.from(module.getDependencies());
      }
    }


    @Override
    public JsonStringMap<String> getBookmarkableState() {
      JsoStringMap<String> map = JsoStringMap.create();
      map.put(MODULE_KEY, module);
      map.put(SRC_KEY, srcDir.join("::"));
      map.put(DEPS_KEY, depsDir.join("::"));
      return map;
    }

    public String getModule() {
      return module;
    }

    public boolean isRecompile() {
      return recompile;
    }

    public JsoArray<String> getSourceDirectory() {
      return srcDir;
    }

    public JsoArray<String> getLibsDirectory() {
      return depsDir;
    }
  }

  public static final GwtCompilePlace PLACE = new GwtCompilePlace();

  private GwtCompilePlace() {
    super(PlaceConstants.GWT_PLACE_NAME);
    setIsStrict(false);
  }

  private final Lazy<String> guessModuleFromHostPage
    = new Lazy<>(() -> {
        //first try guessing from head
        NodeList kids = Browser.getDocument().getHead().getChildNodes();
        int limit = kids.getLength();
        //search backwards, as gwt .nocache.js generally comes after other js imports
        while(limit-->0){
          Node n = kids.item(limit);
          if (n.getNodeName().equalsIgnoreCase("script")){
            Node srcAttr = n.getAttributes().getNamedItem("src");
            if (srcAttr == null) continue;
            String src = srcAttr.getNodeName();
            if (src.contains(".nocache.js")){
              //we have a winner!
              src = src.substring(src.lastIndexOf('/')+1);
              return src.substring(0, src.length() - 11);
            }
          }
        }

        return null;
      });

  @Override
  public PlaceNavigationEvent<? extends Place> createNavigationEvent(
      JsonStringMap<String> decodedState) {
    String srcDir = decodedState.get(NavigationEvent.SRC_KEY);
    if (srcDir == null) {
      srcDir = "";
    }
    String libDir = decodedState.get(NavigationEvent.DEPS_KEY);
    if (libDir == null) {
      libDir = "";
    }
    String module = decodedState.get(NavigationEvent.MODULE_KEY);
    if (module == null){
      //guess our own module source
      module = guessModuleFromHostPage.out1();
    }
    GwtRecompileImpl compile = GwtRecompileImpl.make();
    compile.setModule(module);
    JsoArray<String>
    array = JsoArray.splitString(srcDir, "::");
    compile.setSources(array);
    array = JsoArray.splitString(libDir, "::");
    compile.setDependencies(array);
    return new NavigationEvent(compile);
  }



  /**
   * @param compile the gwt module to compile
   * @return a new navigation event
   */
  public PlaceNavigationEvent<GwtCompilePlace> createNavigationEvent(GwtRecompile compile) {
    return new NavigationEvent(compile);
  }


  @Override
  public boolean isActive() {
    return true;
  }

  public void fireCompile(String module) {
    GwtRecompileImpl compile = GwtRecompileImpl.make();
    compile.setModule(module);
    compile.setIsRecompile(false);
    fire(compile);
  }
  public void fireRecompile(String module){
    GwtRecompileImpl compile = GwtRecompileImpl.make();
    compile.setModule(module);
    compile.setIsRecompile(true);
    fire(compile);
  }

  private void fire(GwtRecompileImpl compile) {
    final PlaceNavigationEvent<?> child = WorkspacePlace.PLACE.getCurrentChildPlaceNavigation();
    new Exception();
    WorkspacePlace.PLACE.disableHistorySnapshotting();
    setIsActive(true, WorkspacePlace.PLACE);
    WorkspacePlace.PLACE.fireChildPlaceNavigation(createNavigationEvent(compile));
    WorkspacePlace.PLACE.enableHistorySnapshotting();

    Browser.getWindow().setTimeout(new TimeoutHandler() {

      @Override
      public void onTimeoutHandler() {
        WorkspacePlace.PLACE.disableHistorySnapshotting();
        WorkspacePlace.PLACE.fireChildPlaceNavigation(child);
        WorkspacePlace.PLACE.enableHistorySnapshotting();
      }
    }, 1);
  }


}
