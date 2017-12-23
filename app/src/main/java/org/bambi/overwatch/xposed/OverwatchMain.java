package org.bambi.overwatch.xposed;

import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static org.bambi.overwatch.xposed.util.XposedUtils.findAndHookBestMethod_failSafe;
import static org.bambi.overwatch.xposed.util.XposedUtils.findAndHookMethod_failSafe;
import static org.bambi.overwatch.xposed.util.XposedUtils.log;
import static org.bambi.overwatch.xposed.util.XposedUtils.unhookMethod_failSafe;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class OverwatchMain implements IXposedHookLoadPackage {

  class ShowHideAnimationHook extends XC_MethodHook {
    private int animationTargetColor;
    private boolean animationShow;

    ShowHideAnimationHook(int animationTargetColor, boolean animationShow) {
      this.animationTargetColor = animationTargetColor;
      this.animationShow = animationShow;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
      log("AnimatorSet.start() hook for %s and %b", Integer.toHexString(animationTargetColor), animationShow);

      AnimatorSet animatorSet = (AnimatorSet)param.thisObject;
      ObjectAnimator backgroundColorAnimator = (ObjectAnimator)animatorSet.getChildAnimations().get(1);

      if(animationShow) {
        backgroundColorAnimator.setIntValues(0, animationTargetColor);
      }
      else {
        backgroundColorAnimator.setIntValues(animationTargetColor, 0);
      }

      animatorSet.setDuration(0L);
    }
  }

  class ShowHideHook extends XC_MethodHook {
    private Unhook showHideAnimationUnhook = null;

    private int animationTargetColor;
    private boolean show;

    ShowHideHook(int animationTargetColor, boolean show) {
      this.animationTargetColor = animationTargetColor;
      this.show = show;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam methodHookParam) {
      showHideAnimationUnhook = findAndHookMethod_failSafe(
          AnimatorSet.class,
          "start",
          new ShowHideAnimationHook(animationTargetColor, show));
    }

    @Override
    protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
      if(showHideAnimationUnhook != null) {
        unhookMethod_failSafe(showHideAnimationUnhook);
        showHideAnimationUnhook = null;
      }
    }
  }


  private static final String NOVA_LAUNCHER_PACKAGE = "com.teslacoilsw.launcher";

  private static final String NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW = "com.teslacoilsw.launcher.AppSearchView";
  private static final String NOVA_LAUNCHER_CLASS__LAUNCHER = "com.android.launcher3.Launcher";

  private static final int APP_SEARCH_VIEW_ANIMATION_TARGET_COLOR = 0xE0080808;

  private Class<?> appSearchViewClass;
  private Class<?> alphaOptimizedEditTextClass;
  private Class<?> circularRevealFrameLayoutClass;


  private void setTopMarginToLayout(XC_MethodHook.MethodHookParam methodHookParam, String layoutVariableName, int addTopMargin) {
    FrameLayout frameLayout = (FrameLayout)getObjectField(methodHookParam.thisObject, layoutVariableName);

//    log(layoutVariableName);
//    logChildViews(frameLayout, "");

    ViewGroup.MarginLayoutParams searchbarLayoutParams = (ViewGroup.MarginLayoutParams)frameLayout.getLayoutParams();
    searchbarLayoutParams.topMargin += addTopMargin;

    frameLayout.setLayoutParams(searchbarLayoutParams);
  }


  private void logChildViews(ViewGroup viewGroup, String prefix) {
    View[] children = (View[])getObjectField(viewGroup, "mChildren");
    int childrenCount = getIntField(viewGroup, "mChildrenCount");

    for(int i = 0; i < childrenCount; i++) {
      log(
          "%sID=%s (%d), isFocusable=%b, isFocusableTM=%b, Class=%s",
          prefix,
          Integer.toHexString(children[i].getId()), children[i].getId(),
          children[i].isFocusable(), children[i].isFocusableInTouchMode(),
          children[i].getClass());

      if(children[i] instanceof ViewGroup) {
        logChildViews((ViewGroup)children[i], prefix + "   ");
      }
    }
  }


  @Override
  public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
    if(lpParam.packageName.equals(NOVA_LAUNCHER_PACKAGE)) {
      log("handleLoadPackage called");

// ====================================================================

      findAndHookMethod_failSafe(
          NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW,
          lpParam.classLoader,
          "setLauncher",
          NOVA_LAUNCHER_CLASS__LAUNCHER,
          new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//              ((LinearLayout)methodHookParam.thisObject).setFocusableInTouchMode(true);
//              ((LinearLayout)methodHookParam.thisObject).setFocusable(true);
//            }

            @Override
            protected void afterHookedMethod(MethodHookParam methodHookParam) {
              LinearLayout mLinearLayout = (LinearLayout)getObjectField(methodHookParam.thisObject, "mLinearLayout");
              mLinearLayout.setBackground(null);

              setTopMarginToLayout(methodHookParam, "mSearchbarBackground", 200);
              setTopMarginToLayout(methodHookParam, "mContentScrollviewParent", 70);
            }
          });

// ====================================================================

      findAndHookBestMethod_failSafe(
          NOVA_LAUNCHER_CLASS__LAUNCHER,
          lpParam.classLoader,
          "eN",
          View.class, Boolean.TYPE, Integer.TYPE, Integer.TYPE,
          new XC_MethodHook() {
            private Unhook toggleKeyboardUnhook;

            @Override
            protected void beforeHookedMethod(MethodHookParam methodHookParam) {
              toggleKeyboardUnhook = findAndHookBestMethod_failSafe(
                  NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW,
                  lpParam.classLoader,
                  "fb",
                  XC_MethodReplacement.DO_NOTHING);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam methodHookParam) {
              if(toggleKeyboardUnhook != null) {
                unhookMethod_failSafe(toggleKeyboardUnhook);
                toggleKeyboardUnhook = null;
              }
            }
          });

// ====================================================================

//      appSearchViewClass = findClass(NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW, lpParam.classLoader);
//      alphaOptimizedEditTextClass = findClass("com.teslacoilsw.launcher.widget.AlphaOptimizedEditText", lpParam.classLoader);
//      circularRevealFrameLayoutClass = findClass("com.teslacoilsw.launcher.anim.CircularRevealFrameLayout", lpParam.classLoader);

// ====================================================================

      findAndHookBestMethod_failSafe(
          NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW,
          lpParam.classLoader,
          "eN",
          Integer.TYPE, Integer.TYPE, Integer.TYPE,
          new ShowHideHook(APP_SEARCH_VIEW_ANIMATION_TARGET_COLOR, true));

// ====================================================================

      findAndHookBestMethod_failSafe(
          NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW,
          lpParam.classLoader,
          "eN",
          Integer.TYPE,
          new ShowHideHook(APP_SEARCH_VIEW_ANIMATION_TARGET_COLOR, false));

// ====================================================================

//      findAndHookMethod(
//          NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW,
//          lpParam.classLoader,
//          "onAttachedToWindow",
//          new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//              XposedUtils.log("before onAttachedToWindow()");
//            }
//          });
//
//      findAndHookMethod(
//          NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW,
//          lpParam.classLoader,
//          "onDetachedFromWindow",
//          new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//              XposedUtils.log("after onDetachedFromWindow()");
//            }
//          });

// ====================================================================

//      findAndHookMethod_failSafe(
//          NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW,
//          lpParam.classLoader,
//          "onAttachedToWindow",
//          new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//              log("before onAttachedToWindow()");
//            }
//          });

//      findAndHookMethod_failSafe(
//          NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW,
//          lpParam.classLoader,
//          "onDetachedFromWindow",
//          new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//              log("after onDetachedFromWindow()");
//            }
//          });

// ====================================================================

//      findAndHookMethod(
//          AnimatorSet.class,
//          "start",
//          new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//              AnimatorSet animatorSet = (AnimatorSet)param.thisObject;
//              ArrayList<Animator> childAnimations = animatorSet.getChildAnimations();
//
//              if(childAnimations.size() == 3) {
//                if(childAnimations.get(0) instanceof ValueAnimator &&
//                   childAnimations.get(1) instanceof ObjectAnimator &&
//                   childAnimations.get(2) instanceof ValueAnimator) {
//
//                  ObjectAnimator objectAnimator = (ObjectAnimator)childAnimations.get(1);
//
//                  if("backgroundColor".equals(objectAnimator.getPropertyName()) &&
//                     objectAnimator.getTarget() != null && objectAnimator.getTarget().getClass().equals(appSearchViewClass)) {
//
//                    if(showAppSearchViewInProgress) {
//                      objectAnimator.setIntValues(0, 0xCC080808);
//                    }
//                    else if(hideAppSearchViewInProgress) {
//                      objectAnimator.setIntValues(0xCC080808, 0);
//                    }
//                    else {
//                      objectAnimator.setIntValues(0, 0);
//                    }
//
//                    animatorSet.setDuration(0L);
//                  }
//                }
//              }
//            }
//          });

// ====================================================================

//      Method showAppSearchView = findMethodBestMatch(
//          findClass(NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW, lpParam.classLoader),
//          "eN",
//          Integer.TYPE, Integer.TYPE, Integer.TYPE);
//
//      hookMethod(showAppSearchView, new XC_MethodHook() {
//        @Override
//        protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//          log("before showAppSearchView");
//          showAppSearchViewInProgress = true;
//        }
//
//        @Override
//        protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//          log("after showAppSearchView");
//          showAppSearchViewInProgress = false;
//
////          LinearLayout mContentLayout = (LinearLayout)getObjectField(methodHookParam.thisObject, "mContentLayout");
////          View[] children = (View[])getObjectField(mContentLayout, "mChildren");
////          View view = ((View[])getObjectField(children[0], "mChildren"))[0];
//
////          logChildViews(mContentLayout, "");
//
////          View[] children = (View[])getObjectField(mContentLayout, "mChildren");
////          int childrenCount = getIntField(mContentLayout, "mChildrenCount");
////
////          for(int i = 0; i < childrenCount; i++) {
////            log("child.getId() = " + children[i].getId() + " -> " + Integer.toHexString(children[i].getId()));
////            log("child.getClass() = " + children[i].getClass());
////            log("child.getContentDescription() = " + children[i].getContentDescription());
////          }
//
////          View view = children[0].findViewById(2131362279);
////          View view = (View)callMethod(mContentLayout, "findViewById", 2131362279);
////          log("view = " + view);
////
////          if(view != null) {
////            log("view.isFocusable() = " + view.isFocusable());
////            log("request focus: " + view.requestFocus());
////          }
//        }
//      });

// ====================================================================

//      Method hideAppSearchView = findMethodBestMatch(
//          findClass(NOVA_LAUNCHER_CLASS__APP_SEARCH_VIEW, lpParam.classLoader),
//          "eN",
//          Integer.TYPE);
//
//      hookMethod(hideAppSearchView, new XC_MethodHook() {
//        @Override
//        protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//          log("before hideAppSearchView");
//          hideAppSearchViewInProgress = true;
//        }
//
//        @Override
//        protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//          log("after hideAppSearchView");
//          hideAppSearchViewInProgress = false;
//        }
//      });

// ====================================================================

    }
  }

//  @Override
//  public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
//    if(lpParam.packageName.equals(NOVA_LAUNCHER_PACKAGE)) {
//      log("handleLoadPackage called");
//
//      findAndHookConstructor(
//          NOVA_LAUNCHER_CLASS__DRAWER_SEARCH_VIEW_BINDING,
//          lpParam.classLoader,
//          NOVA_LAUNCHER_CLASS__DRAWER_SEARCH_VIEW, View.class,
//          new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//              super.afterHookedMethod(param);
//
//              LinearLayout mContentLayout = (LinearLayout)getObjectField(param.args[0], "mContentLayout");
//
//              log("mContentLayout.getTranslationY() = " + mContentLayout.getTranslationY());
//              log("mContentLayout.getTop() = " + mContentLayout.getTop());
//              log("mContentLayout.getId() = " + Integer.toHexString(mContentLayout.getId()));
//
//              mContentLayout.setTranslationY(270);
//
//              log("mContentLayout.getAlpha() = " + mContentLayout.getAlpha());
//              log("mContentLayout.getBackground() = " + mContentLayout.getBackground());
//
////              mContentLayout.setAlpha(0.1f);
//
//              if(mContentLayout.getBackground() != null) {
//                log("mContentLayout.getBackground().getAlpha() = " + mContentLayout.getBackground().getAlpha());
//                log("mContentLayout.getBackground().getOpacity() = " + mContentLayout.getBackground().getOpacity());
//              }
//
//              LinearLayout mLinearLayout = (LinearLayout)getObjectField(param.args[0], "mLinearLayout");
//
//              log("mLinearLayout.getId() = " + Integer.toHexString(mLinearLayout.getId()));
//              log("mLinearLayout.getAlpha() = " + mLinearLayout.getAlpha());
//              log("mLinearLayout.getBackground() = " + mLinearLayout.getBackground());
//              log("mLinearLayout.getParent() = " + mLinearLayout.getParent());
//
//              if(mLinearLayout.getBackground() != null) {
//                log("mLinearLayout.getBackground().getAlpha() = " + mLinearLayout.getBackground().getAlpha());
//                log("mLinearLayout.getBackground().getOpacity() = " + mLinearLayout.getBackground().getOpacity());
//              }
//
//              LinearLayout appSearchView = (LinearLayout)mLinearLayout.getParent();
//
//              log("appSearchView.getId() = " + Integer.toHexString(appSearchView.getId()));
//              log("appSearchView.getAlpha() = " + appSearchView.getAlpha());
//              log("appSearchView.getBackground() = " + appSearchView.getBackground());
//
//              if(appSearchView.getBackground() != null) {
//                log("appSearchView.getBackground().getAlpha() = " + appSearchView.getBackground().getAlpha());
//                log("appSearchView.getBackground().getOpacity() = " + appSearchView.getBackground().getOpacity());
//              }
//
//            }
//          });
//
//    }
//  }

}
