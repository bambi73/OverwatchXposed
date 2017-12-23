package org.bambi.overwatch.xposed.util;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodBestMatch;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class XposedUtils {

  public static final String OVERWATCH_LOG_PREFIX = "##### Overwatch";


  public static void log(String message, Object... parameters) {
    XposedBridge.log(String.format(String.format("%s: %s", OVERWATCH_LOG_PREFIX, message), parameters));
  }


  private static XC_MethodHook getCallback(Object... parameterTypesAndCallback) {
    if(parameterTypesAndCallback.length == 0 || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook)) {
      throw new IllegalArgumentException("No method callback defined");
    }

    return (XC_MethodHook)parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
  }


  private static Class<?>[] getParameterTypes(ClassLoader classLoader, Object... parameterTypesAndCallback) {
    if(parameterTypesAndCallback.length > 1) {
      Class<?>[] parameterClasses = new Class<?>[parameterTypesAndCallback.length - 1];

      for(int i = 0; i < parameterClasses.length; i++) {
        Object type = parameterTypesAndCallback[i];

        if(type instanceof Class) {
          parameterClasses[i] = (Class<?>)type;
        }
        else if(type instanceof String) {
          parameterClasses[i] = findClass((String)type, classLoader);
        }
        else {
          throw new IllegalArgumentException("parameter type must either be specified as Class or String");
        }
      }

      return parameterClasses;
    }
    else {
      return new Class<?>[0];
    }
  }


  public static XC_MethodHook.Unhook findAndHookMethod_failSafe(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
    XC_MethodHook.Unhook unhook = null;

    try {
      unhook = findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
    }
    catch(Throwable exception) {
      log("Failed to hook method %s.%s", clazz.getSimpleName(), methodName);
      XposedBridge.log(exception);
    }

    return unhook;
  }


  public static XC_MethodHook.Unhook findAndHookMethod_failSafe(
      String clazzName, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {

    XC_MethodHook.Unhook unhook = null;

    try {
      unhook = findAndHookMethod(clazzName, classLoader, methodName, parameterTypesAndCallback);
    }
    catch(Throwable exception) {
      log("Failed to hook method %s.%s", clazzName, methodName);
      XposedBridge.log(exception);
    }

    return unhook;
  }


  public static XC_MethodHook.Unhook findAndHookBestMethod_failSafe(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
    XC_MethodHook.Unhook unhook = null;

    try {
      XC_MethodHook methodHook = getCallback(parameterTypesAndCallback);

      Method method = findMethodBestMatch(clazz, methodName, getParameterTypes(clazz.getClassLoader(), parameterTypesAndCallback));
      unhook = hookMethod(method, methodHook);
    }
    catch(Throwable exception) {
      log("Failed to hook method %s.%s", clazz.getSimpleName(), methodName);
      XposedBridge.log(exception);
    }

    return unhook;
  }


  public static XC_MethodHook.Unhook findAndHookBestMethod_failSafe(
      String clazzName, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {

    XC_MethodHook.Unhook unhook = null;

    try {
      XC_MethodHook methodHook = getCallback(parameterTypesAndCallback);

      Method method = findMethodBestMatch(findClass(clazzName, classLoader), methodName, getParameterTypes(classLoader, parameterTypesAndCallback));
      unhook = hookMethod(method, methodHook);
    }
    catch(Throwable exception) {
      log("Failed to hook method %s.%s", clazzName, methodName);
      XposedBridge.log(exception);
    }

    return unhook;
  }


  public static void unhookMethod_failSafe(XC_MethodHook.Unhook unhook) {
    try {
      unhook.unhook();
    }
    catch(Throwable exception) {
      log("Failed to unhook method %s.%s", unhook.getHookedMethod().getDeclaringClass().getSimpleName(), unhook.getHookedMethod().getName());
      XposedBridge.log(exception);
    }
  }

}
