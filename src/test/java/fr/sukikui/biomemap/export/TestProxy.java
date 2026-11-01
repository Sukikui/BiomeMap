package fr.sukikui.biomemap.export;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/** Strict interface doubles used to exercise the Paper boundary without a running server. */
final class TestProxy {

  private TestProxy() {
  }

  static <T> T create(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
              case "toString" -> type.getSimpleName() + " test double";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> throw new AssertionError(method.getName());
            };
          }
          return handler.invoke(proxy, method, args);
        }));
  }
}
