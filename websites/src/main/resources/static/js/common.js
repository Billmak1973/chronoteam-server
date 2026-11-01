"use strict";
var ChronoTeamApp = (() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __commonJS = (cb, mod) => function __require() {
    try {
      return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
    } catch (e) {
      throw mod = 0, e;
    }
  };
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
    // If the importer is in node compatibility mode or this is not an ESM
    // file that has been converted to a CommonJS file using a Babel-
    // compatible transform (i.e. "__esModule" has not been set), then set
    // "default" to the CommonJS "module.exports" for node compatibility.
    isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
    mod
  ));
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // node_modules/react/cjs/react.development.js
  var require_react_development = __commonJS({
    "node_modules/react/cjs/react.development.js"(exports, module) {
      "use strict";
      (function() {
        function defineDeprecationWarning(methodName, info) {
          Object.defineProperty(Component.prototype, methodName, {
            get: function() {
              console.warn(
                "%s(...) is deprecated in plain JavaScript React classes. %s",
                info[0],
                info[1]
              );
            }
          });
        }
        function getIteratorFn(maybeIterable) {
          if (null === maybeIterable || "object" !== typeof maybeIterable)
            return null;
          maybeIterable = MAYBE_ITERATOR_SYMBOL && maybeIterable[MAYBE_ITERATOR_SYMBOL] || maybeIterable["@@iterator"];
          return "function" === typeof maybeIterable ? maybeIterable : null;
        }
        function warnNoop(publicInstance, callerName) {
          publicInstance = (publicInstance = publicInstance.constructor) && (publicInstance.displayName || publicInstance.name) || "ReactClass";
          var warningKey = publicInstance + "." + callerName;
          didWarnStateUpdateForUnmountedComponent[warningKey] || (console.error(
            "Can't call %s on a component that is not yet mounted. This is a no-op, but it might indicate a bug in your application. Instead, assign to `this.state` directly or define a `state = {};` class property with the desired state in the %s component.",
            callerName,
            publicInstance
          ), didWarnStateUpdateForUnmountedComponent[warningKey] = true);
        }
        function Component(props, context, updater) {
          this.props = props;
          this.context = context;
          this.refs = emptyObject;
          this.updater = updater || ReactNoopUpdateQueue;
        }
        function ComponentDummy() {
        }
        function PureComponent(props, context, updater) {
          this.props = props;
          this.context = context;
          this.refs = emptyObject;
          this.updater = updater || ReactNoopUpdateQueue;
        }
        function noop() {
        }
        function testStringCoercion(value) {
          return "" + value;
        }
        function checkKeyStringCoercion(value) {
          try {
            testStringCoercion(value);
            var JSCompiler_inline_result = false;
          } catch (e) {
            JSCompiler_inline_result = true;
          }
          if (JSCompiler_inline_result) {
            JSCompiler_inline_result = console;
            var JSCompiler_temp_const = JSCompiler_inline_result.error;
            var JSCompiler_inline_result$jscomp$0 = "function" === typeof Symbol && Symbol.toStringTag && value[Symbol.toStringTag] || value.constructor.name || "Object";
            JSCompiler_temp_const.call(
              JSCompiler_inline_result,
              "The provided key is an unsupported type %s. This value must be coerced to a string before using it here.",
              JSCompiler_inline_result$jscomp$0
            );
            return testStringCoercion(value);
          }
        }
        function getComponentNameFromType(type) {
          if (null == type) return null;
          if ("function" === typeof type)
            return type.$$typeof === REACT_CLIENT_REFERENCE ? null : type.displayName || type.name || null;
          if ("string" === typeof type) return type;
          switch (type) {
            case REACT_FRAGMENT_TYPE:
              return "Fragment";
            case REACT_PROFILER_TYPE:
              return "Profiler";
            case REACT_STRICT_MODE_TYPE:
              return "StrictMode";
            case REACT_SUSPENSE_TYPE:
              return "Suspense";
            case REACT_SUSPENSE_LIST_TYPE:
              return "SuspenseList";
            case REACT_ACTIVITY_TYPE:
              return "Activity";
          }
          if ("object" === typeof type)
            switch ("number" === typeof type.tag && console.error(
              "Received an unexpected object in getComponentNameFromType(). This is likely a bug in React. Please file an issue."
            ), type.$$typeof) {
              case REACT_PORTAL_TYPE:
                return "Portal";
              case REACT_CONTEXT_TYPE:
                return type.displayName || "Context";
              case REACT_CONSUMER_TYPE:
                return (type._context.displayName || "Context") + ".Consumer";
              case REACT_FORWARD_REF_TYPE:
                var innerType = type.render;
                type = type.displayName;
                type || (type = innerType.displayName || innerType.name || "", type = "" !== type ? "ForwardRef(" + type + ")" : "ForwardRef");
                return type;
              case REACT_MEMO_TYPE:
                return innerType = type.displayName || null, null !== innerType ? innerType : getComponentNameFromType(type.type) || "Memo";
              case REACT_LAZY_TYPE:
                innerType = type._payload;
                type = type._init;
                try {
                  return getComponentNameFromType(type(innerType));
                } catch (x) {
                }
            }
          return null;
        }
        function getTaskName(type) {
          if (type === REACT_FRAGMENT_TYPE) return "<>";
          if ("object" === typeof type && null !== type && type.$$typeof === REACT_LAZY_TYPE)
            return "<...>";
          try {
            var name = getComponentNameFromType(type);
            return name ? "<" + name + ">" : "<...>";
          } catch (x) {
            return "<...>";
          }
        }
        function getOwner() {
          var dispatcher = ReactSharedInternals.A;
          return null === dispatcher ? null : dispatcher.getOwner();
        }
        function UnknownOwner() {
          return Error("react-stack-top-frame");
        }
        function hasValidKey(config) {
          if (hasOwnProperty.call(config, "key")) {
            var getter = Object.getOwnPropertyDescriptor(config, "key").get;
            if (getter && getter.isReactWarning) return false;
          }
          return void 0 !== config.key;
        }
        function defineKeyPropWarningGetter(props, displayName) {
          function warnAboutAccessingKey() {
            specialPropKeyWarningShown || (specialPropKeyWarningShown = true, console.error(
              "%s: `key` is not a prop. Trying to access it will result in `undefined` being returned. If you need to access the same value within the child component, you should pass it as a different prop. (https://react.dev/link/special-props)",
              displayName
            ));
          }
          warnAboutAccessingKey.isReactWarning = true;
          Object.defineProperty(props, "key", {
            get: warnAboutAccessingKey,
            configurable: true
          });
        }
        function elementRefGetterWithDeprecationWarning() {
          var componentName = getComponentNameFromType(this.type);
          didWarnAboutElementRef[componentName] || (didWarnAboutElementRef[componentName] = true, console.error(
            "Accessing element.ref was removed in React 19. ref is now a regular prop. It will be removed from the JSX Element type in a future release."
          ));
          componentName = this.props.ref;
          return void 0 !== componentName ? componentName : null;
        }
        function ReactElement(type, key, props, owner, debugStack, debugTask) {
          var refProp = props.ref;
          type = {
            $$typeof: REACT_ELEMENT_TYPE,
            type,
            key,
            props,
            _owner: owner
          };
          null !== (void 0 !== refProp ? refProp : null) ? Object.defineProperty(type, "ref", {
            enumerable: false,
            get: elementRefGetterWithDeprecationWarning
          }) : Object.defineProperty(type, "ref", { enumerable: false, value: null });
          type._store = {};
          Object.defineProperty(type._store, "validated", {
            configurable: false,
            enumerable: false,
            writable: true,
            value: 0
          });
          Object.defineProperty(type, "_debugInfo", {
            configurable: false,
            enumerable: false,
            writable: true,
            value: null
          });
          Object.defineProperty(type, "_debugStack", {
            configurable: false,
            enumerable: false,
            writable: true,
            value: debugStack
          });
          Object.defineProperty(type, "_debugTask", {
            configurable: false,
            enumerable: false,
            writable: true,
            value: debugTask
          });
          Object.freeze && (Object.freeze(type.props), Object.freeze(type));
          return type;
        }
        function cloneAndReplaceKey(oldElement, newKey) {
          newKey = ReactElement(
            oldElement.type,
            newKey,
            oldElement.props,
            oldElement._owner,
            oldElement._debugStack,
            oldElement._debugTask
          );
          oldElement._store && (newKey._store.validated = oldElement._store.validated);
          return newKey;
        }
        function validateChildKeys(node) {
          isValidElement(node) ? node._store && (node._store.validated = 1) : "object" === typeof node && null !== node && node.$$typeof === REACT_LAZY_TYPE && ("fulfilled" === node._payload.status ? isValidElement(node._payload.value) && node._payload.value._store && (node._payload.value._store.validated = 1) : node._store && (node._store.validated = 1));
        }
        function isValidElement(object) {
          return "object" === typeof object && null !== object && object.$$typeof === REACT_ELEMENT_TYPE;
        }
        function escape(key) {
          var escaperLookup = { "=": "=0", ":": "=2" };
          return "$" + key.replace(/[=:]/g, function(match) {
            return escaperLookup[match];
          });
        }
        function getElementKey(element, index) {
          return "object" === typeof element && null !== element && null != element.key ? (checkKeyStringCoercion(element.key), escape("" + element.key)) : index.toString(36);
        }
        function resolveThenable(thenable) {
          switch (thenable.status) {
            case "fulfilled":
              return thenable.value;
            case "rejected":
              throw thenable.reason;
            default:
              switch ("string" === typeof thenable.status ? thenable.then(noop, noop) : (thenable.status = "pending", thenable.then(
                function(fulfilledValue) {
                  "pending" === thenable.status && (thenable.status = "fulfilled", thenable.value = fulfilledValue);
                },
                function(error) {
                  "pending" === thenable.status && (thenable.status = "rejected", thenable.reason = error);
                }
              )), thenable.status) {
                case "fulfilled":
                  return thenable.value;
                case "rejected":
                  throw thenable.reason;
              }
          }
          throw thenable;
        }
        function mapIntoArray(children, array, escapedPrefix, nameSoFar, callback) {
          var type = typeof children;
          if ("undefined" === type || "boolean" === type) children = null;
          var invokeCallback = false;
          if (null === children) invokeCallback = true;
          else
            switch (type) {
              case "bigint":
              case "string":
              case "number":
                invokeCallback = true;
                break;
              case "object":
                switch (children.$$typeof) {
                  case REACT_ELEMENT_TYPE:
                  case REACT_PORTAL_TYPE:
                    invokeCallback = true;
                    break;
                  case REACT_LAZY_TYPE:
                    return invokeCallback = children._init, mapIntoArray(
                      invokeCallback(children._payload),
                      array,
                      escapedPrefix,
                      nameSoFar,
                      callback
                    );
                }
            }
          if (invokeCallback) {
            invokeCallback = children;
            callback = callback(invokeCallback);
            var childKey = "" === nameSoFar ? "." + getElementKey(invokeCallback, 0) : nameSoFar;
            isArrayImpl(callback) ? (escapedPrefix = "", null != childKey && (escapedPrefix = childKey.replace(userProvidedKeyEscapeRegex, "$&/") + "/"), mapIntoArray(callback, array, escapedPrefix, "", function(c) {
              return c;
            })) : null != callback && (isValidElement(callback) && (null != callback.key && (invokeCallback && invokeCallback.key === callback.key || checkKeyStringCoercion(callback.key)), escapedPrefix = cloneAndReplaceKey(
              callback,
              escapedPrefix + (null == callback.key || invokeCallback && invokeCallback.key === callback.key ? "" : ("" + callback.key).replace(
                userProvidedKeyEscapeRegex,
                "$&/"
              ) + "/") + childKey
            ), "" !== nameSoFar && null != invokeCallback && isValidElement(invokeCallback) && null == invokeCallback.key && invokeCallback._store && !invokeCallback._store.validated && (escapedPrefix._store.validated = 2), callback = escapedPrefix), array.push(callback));
            return 1;
          }
          invokeCallback = 0;
          childKey = "" === nameSoFar ? "." : nameSoFar + ":";
          if (isArrayImpl(children))
            for (var i = 0; i < children.length; i++)
              nameSoFar = children[i], type = childKey + getElementKey(nameSoFar, i), invokeCallback += mapIntoArray(
                nameSoFar,
                array,
                escapedPrefix,
                type,
                callback
              );
          else if (i = getIteratorFn(children), "function" === typeof i)
            for (i === children.entries && (didWarnAboutMaps || console.warn(
              "Using Maps as children is not supported. Use an array of keyed ReactElements instead."
            ), didWarnAboutMaps = true), children = i.call(children), i = 0; !(nameSoFar = children.next()).done; )
              nameSoFar = nameSoFar.value, type = childKey + getElementKey(nameSoFar, i++), invokeCallback += mapIntoArray(
                nameSoFar,
                array,
                escapedPrefix,
                type,
                callback
              );
          else if ("object" === type) {
            if ("function" === typeof children.then)
              return mapIntoArray(
                resolveThenable(children),
                array,
                escapedPrefix,
                nameSoFar,
                callback
              );
            array = String(children);
            throw Error(
              "Objects are not valid as a React child (found: " + ("[object Object]" === array ? "object with keys {" + Object.keys(children).join(", ") + "}" : array) + "). If you meant to render a collection of children, use an array instead."
            );
          }
          return invokeCallback;
        }
        function mapChildren(children, func, context) {
          if (null == children) return children;
          var result = [], count = 0;
          mapIntoArray(children, result, "", "", function(child) {
            return func.call(context, child, count++);
          });
          return result;
        }
        function lazyInitializer(payload) {
          if (-1 === payload._status) {
            var ioInfo = payload._ioInfo;
            null != ioInfo && (ioInfo.start = ioInfo.end = performance.now());
            ioInfo = payload._result;
            var thenable = ioInfo();
            thenable.then(
              function(moduleObject) {
                if (0 === payload._status || -1 === payload._status) {
                  payload._status = 1;
                  payload._result = moduleObject;
                  var _ioInfo = payload._ioInfo;
                  null != _ioInfo && (_ioInfo.end = performance.now());
                  void 0 === thenable.status && (thenable.status = "fulfilled", thenable.value = moduleObject);
                }
              },
              function(error) {
                if (0 === payload._status || -1 === payload._status) {
                  payload._status = 2;
                  payload._result = error;
                  var _ioInfo2 = payload._ioInfo;
                  null != _ioInfo2 && (_ioInfo2.end = performance.now());
                  void 0 === thenable.status && (thenable.status = "rejected", thenable.reason = error);
                }
              }
            );
            ioInfo = payload._ioInfo;
            if (null != ioInfo) {
              ioInfo.value = thenable;
              var displayName = thenable.displayName;
              "string" === typeof displayName && (ioInfo.name = displayName);
            }
            -1 === payload._status && (payload._status = 0, payload._result = thenable);
          }
          if (1 === payload._status)
            return ioInfo = payload._result, void 0 === ioInfo && console.error(
              "lazy: Expected the result of a dynamic import() call. Instead received: %s\n\nYour code should look like: \n  const MyComponent = lazy(() => import('./MyComponent'))\n\nDid you accidentally put curly braces around the import?",
              ioInfo
            ), "default" in ioInfo || console.error(
              "lazy: Expected the result of a dynamic import() call. Instead received: %s\n\nYour code should look like: \n  const MyComponent = lazy(() => import('./MyComponent'))",
              ioInfo
            ), ioInfo.default;
          throw payload._result;
        }
        function resolveDispatcher() {
          var dispatcher = ReactSharedInternals.H;
          null === dispatcher && console.error(
            "Invalid hook call. Hooks can only be called inside of the body of a function component. This could happen for one of the following reasons:\n1. You might have mismatching versions of React and the renderer (such as React DOM)\n2. You might be breaking the Rules of Hooks\n3. You might have more than one copy of React in the same app\nSee https://react.dev/link/invalid-hook-call for tips about how to debug and fix this problem."
          );
          return dispatcher;
        }
        function releaseAsyncTransition() {
          ReactSharedInternals.asyncTransitions--;
        }
        function enqueueTask(task) {
          if (null === enqueueTaskImpl)
            try {
              var requireString = ("require" + Math.random()).slice(0, 7);
              enqueueTaskImpl = (module && module[requireString]).call(
                module,
                "timers"
              ).setImmediate;
            } catch (_err) {
              enqueueTaskImpl = function(callback) {
                false === didWarnAboutMessageChannel && (didWarnAboutMessageChannel = true, "undefined" === typeof MessageChannel && console.error(
                  "This browser does not have a MessageChannel implementation, so enqueuing tasks via await act(async () => ...) will fail. Please file an issue at https://github.com/facebook/react/issues if you encounter this warning."
                ));
                var channel = new MessageChannel();
                channel.port1.onmessage = callback;
                channel.port2.postMessage(void 0);
              };
            }
          return enqueueTaskImpl(task);
        }
        function aggregateErrors(errors) {
          return 1 < errors.length && "function" === typeof AggregateError ? new AggregateError(errors) : errors[0];
        }
        function popActScope(prevActQueue, prevActScopeDepth) {
          prevActScopeDepth !== actScopeDepth - 1 && console.error(
            "You seem to have overlapping act() calls, this is not supported. Be sure to await previous act() calls before making a new one. "
          );
          actScopeDepth = prevActScopeDepth;
        }
        function recursivelyFlushAsyncActWork(returnValue, resolve, reject) {
          var queue = ReactSharedInternals.actQueue;
          if (null !== queue)
            if (0 !== queue.length)
              try {
                flushActQueue(queue);
                enqueueTask(function() {
                  return recursivelyFlushAsyncActWork(returnValue, resolve, reject);
                });
                return;
              } catch (error) {
                ReactSharedInternals.thrownErrors.push(error);
              }
            else ReactSharedInternals.actQueue = null;
          0 < ReactSharedInternals.thrownErrors.length ? (queue = aggregateErrors(ReactSharedInternals.thrownErrors), ReactSharedInternals.thrownErrors.length = 0, reject(queue)) : resolve(returnValue);
        }
        function flushActQueue(queue) {
          if (!isFlushing) {
            isFlushing = true;
            var i = 0;
            try {
              for (; i < queue.length; i++) {
                var callback = queue[i];
                do {
                  ReactSharedInternals.didUsePromise = false;
                  var continuation = callback(false);
                  if (null !== continuation) {
                    if (ReactSharedInternals.didUsePromise) {
                      queue[i] = callback;
                      queue.splice(0, i);
                      return;
                    }
                    callback = continuation;
                  } else break;
                } while (1);
              }
              queue.length = 0;
            } catch (error) {
              queue.splice(0, i + 1), ReactSharedInternals.thrownErrors.push(error);
            } finally {
              isFlushing = false;
            }
          }
        }
        "undefined" !== typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ && "function" === typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStart && __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStart(Error());
        var REACT_ELEMENT_TYPE = /* @__PURE__ */ Symbol.for("react.transitional.element"), REACT_PORTAL_TYPE = /* @__PURE__ */ Symbol.for("react.portal"), REACT_FRAGMENT_TYPE = /* @__PURE__ */ Symbol.for("react.fragment"), REACT_STRICT_MODE_TYPE = /* @__PURE__ */ Symbol.for("react.strict_mode"), REACT_PROFILER_TYPE = /* @__PURE__ */ Symbol.for("react.profiler"), REACT_CONSUMER_TYPE = /* @__PURE__ */ Symbol.for("react.consumer"), REACT_CONTEXT_TYPE = /* @__PURE__ */ Symbol.for("react.context"), REACT_FORWARD_REF_TYPE = /* @__PURE__ */ Symbol.for("react.forward_ref"), REACT_SUSPENSE_TYPE = /* @__PURE__ */ Symbol.for("react.suspense"), REACT_SUSPENSE_LIST_TYPE = /* @__PURE__ */ Symbol.for("react.suspense_list"), REACT_MEMO_TYPE = /* @__PURE__ */ Symbol.for("react.memo"), REACT_LAZY_TYPE = /* @__PURE__ */ Symbol.for("react.lazy"), REACT_ACTIVITY_TYPE = /* @__PURE__ */ Symbol.for("react.activity"), MAYBE_ITERATOR_SYMBOL = Symbol.iterator, didWarnStateUpdateForUnmountedComponent = {}, ReactNoopUpdateQueue = {
          isMounted: function() {
            return false;
          },
          enqueueForceUpdate: function(publicInstance) {
            warnNoop(publicInstance, "forceUpdate");
          },
          enqueueReplaceState: function(publicInstance) {
            warnNoop(publicInstance, "replaceState");
          },
          enqueueSetState: function(publicInstance) {
            warnNoop(publicInstance, "setState");
          }
        }, assign = Object.assign, emptyObject = {};
        Object.freeze(emptyObject);
        Component.prototype.isReactComponent = {};
        Component.prototype.setState = function(partialState, callback) {
          if ("object" !== typeof partialState && "function" !== typeof partialState && null != partialState)
            throw Error(
              "takes an object of state variables to update or a function which returns an object of state variables."
            );
          this.updater.enqueueSetState(this, partialState, callback, "setState");
        };
        Component.prototype.forceUpdate = function(callback) {
          this.updater.enqueueForceUpdate(this, callback, "forceUpdate");
        };
        var deprecatedAPIs = {
          isMounted: [
            "isMounted",
            "Instead, make sure to clean up subscriptions and pending requests in componentWillUnmount to prevent memory leaks."
          ],
          replaceState: [
            "replaceState",
            "Refactor your code to use setState instead (see https://github.com/facebook/react/issues/3236)."
          ]
        };
        for (fnName in deprecatedAPIs)
          deprecatedAPIs.hasOwnProperty(fnName) && defineDeprecationWarning(fnName, deprecatedAPIs[fnName]);
        ComponentDummy.prototype = Component.prototype;
        deprecatedAPIs = PureComponent.prototype = new ComponentDummy();
        deprecatedAPIs.constructor = PureComponent;
        assign(deprecatedAPIs, Component.prototype);
        deprecatedAPIs.isPureReactComponent = true;
        var isArrayImpl = Array.isArray, REACT_CLIENT_REFERENCE = /* @__PURE__ */ Symbol.for("react.client.reference"), ReactSharedInternals = {
          H: null,
          A: null,
          T: null,
          S: null,
          actQueue: null,
          asyncTransitions: 0,
          isBatchingLegacy: false,
          didScheduleLegacyUpdate: false,
          didUsePromise: false,
          thrownErrors: [],
          getCurrentStack: null,
          recentlyCreatedOwnerStacks: 0
        }, hasOwnProperty = Object.prototype.hasOwnProperty, createTask = console.createTask ? console.createTask : function() {
          return null;
        };
        deprecatedAPIs = {
          react_stack_bottom_frame: function(callStackForError) {
            return callStackForError();
          }
        };
        var specialPropKeyWarningShown, didWarnAboutOldJSXRuntime;
        var didWarnAboutElementRef = {};
        var unknownOwnerDebugStack = deprecatedAPIs.react_stack_bottom_frame.bind(
          deprecatedAPIs,
          UnknownOwner
        )();
        var unknownOwnerDebugTask = createTask(getTaskName(UnknownOwner));
        var didWarnAboutMaps = false, userProvidedKeyEscapeRegex = /\/+/g, reportGlobalError = "function" === typeof reportError ? reportError : function(error) {
          if ("object" === typeof window && "function" === typeof window.ErrorEvent) {
            var event = new window.ErrorEvent("error", {
              bubbles: true,
              cancelable: true,
              message: "object" === typeof error && null !== error && "string" === typeof error.message ? String(error.message) : String(error),
              error
            });
            if (!window.dispatchEvent(event)) return;
          } else if ("object" === typeof process && "function" === typeof process.emit) {
            process.emit("uncaughtException", error);
            return;
          }
          console.error(error);
        }, didWarnAboutMessageChannel = false, enqueueTaskImpl = null, actScopeDepth = 0, didWarnNoAwaitAct = false, isFlushing = false, queueSeveralMicrotasks = "function" === typeof queueMicrotask ? function(callback) {
          queueMicrotask(function() {
            return queueMicrotask(callback);
          });
        } : enqueueTask;
        deprecatedAPIs = Object.freeze({
          __proto__: null,
          c: function(size) {
            return resolveDispatcher().useMemoCache(size);
          }
        });
        var fnName = {
          map: mapChildren,
          forEach: function(children, forEachFunc, forEachContext) {
            mapChildren(
              children,
              function() {
                forEachFunc.apply(this, arguments);
              },
              forEachContext
            );
          },
          count: function(children) {
            var n = 0;
            mapChildren(children, function() {
              n++;
            });
            return n;
          },
          toArray: function(children) {
            return mapChildren(children, function(child) {
              return child;
            }) || [];
          },
          only: function(children) {
            if (!isValidElement(children))
              throw Error(
                "React.Children.only expected to receive a single React element child."
              );
            return children;
          }
        };
        exports.Activity = REACT_ACTIVITY_TYPE;
        exports.Children = fnName;
        exports.Component = Component;
        exports.Fragment = REACT_FRAGMENT_TYPE;
        exports.Profiler = REACT_PROFILER_TYPE;
        exports.PureComponent = PureComponent;
        exports.StrictMode = REACT_STRICT_MODE_TYPE;
        exports.Suspense = REACT_SUSPENSE_TYPE;
        exports.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE = ReactSharedInternals;
        exports.__COMPILER_RUNTIME = deprecatedAPIs;
        exports.act = function(callback) {
          var prevActQueue = ReactSharedInternals.actQueue, prevActScopeDepth = actScopeDepth;
          actScopeDepth++;
          var queue = ReactSharedInternals.actQueue = null !== prevActQueue ? prevActQueue : [], didAwaitActCall = false;
          try {
            var result = callback();
          } catch (error) {
            ReactSharedInternals.thrownErrors.push(error);
          }
          if (0 < ReactSharedInternals.thrownErrors.length)
            throw popActScope(prevActQueue, prevActScopeDepth), callback = aggregateErrors(ReactSharedInternals.thrownErrors), ReactSharedInternals.thrownErrors.length = 0, callback;
          if (null !== result && "object" === typeof result && "function" === typeof result.then) {
            var thenable = result;
            queueSeveralMicrotasks(function() {
              didAwaitActCall || didWarnNoAwaitAct || (didWarnNoAwaitAct = true, console.error(
                "You called act(async () => ...) without await. This could lead to unexpected testing behaviour, interleaving multiple act calls and mixing their scopes. You should - await act(async () => ...);"
              ));
            });
            return {
              then: function(resolve, reject) {
                didAwaitActCall = true;
                thenable.then(
                  function(returnValue) {
                    popActScope(prevActQueue, prevActScopeDepth);
                    if (0 === prevActScopeDepth) {
                      try {
                        flushActQueue(queue), enqueueTask(function() {
                          return recursivelyFlushAsyncActWork(
                            returnValue,
                            resolve,
                            reject
                          );
                        });
                      } catch (error$0) {
                        ReactSharedInternals.thrownErrors.push(error$0);
                      }
                      if (0 < ReactSharedInternals.thrownErrors.length) {
                        var _thrownError = aggregateErrors(
                          ReactSharedInternals.thrownErrors
                        );
                        ReactSharedInternals.thrownErrors.length = 0;
                        reject(_thrownError);
                      }
                    } else resolve(returnValue);
                  },
                  function(error) {
                    popActScope(prevActQueue, prevActScopeDepth);
                    0 < ReactSharedInternals.thrownErrors.length ? (error = aggregateErrors(
                      ReactSharedInternals.thrownErrors
                    ), ReactSharedInternals.thrownErrors.length = 0, reject(error)) : reject(error);
                  }
                );
              }
            };
          }
          var returnValue$jscomp$0 = result;
          popActScope(prevActQueue, prevActScopeDepth);
          0 === prevActScopeDepth && (flushActQueue(queue), 0 !== queue.length && queueSeveralMicrotasks(function() {
            didAwaitActCall || didWarnNoAwaitAct || (didWarnNoAwaitAct = true, console.error(
              "A component suspended inside an `act` scope, but the `act` call was not awaited. When testing React components that depend on asynchronous data, you must await the result:\n\nawait act(() => ...)"
            ));
          }), ReactSharedInternals.actQueue = null);
          if (0 < ReactSharedInternals.thrownErrors.length)
            throw callback = aggregateErrors(ReactSharedInternals.thrownErrors), ReactSharedInternals.thrownErrors.length = 0, callback;
          return {
            then: function(resolve, reject) {
              didAwaitActCall = true;
              0 === prevActScopeDepth ? (ReactSharedInternals.actQueue = queue, enqueueTask(function() {
                return recursivelyFlushAsyncActWork(
                  returnValue$jscomp$0,
                  resolve,
                  reject
                );
              })) : resolve(returnValue$jscomp$0);
            }
          };
        };
        exports.cache = function(fn) {
          return function() {
            return fn.apply(null, arguments);
          };
        };
        exports.cacheSignal = function() {
          return null;
        };
        exports.captureOwnerStack = function() {
          var getCurrentStack = ReactSharedInternals.getCurrentStack;
          return null === getCurrentStack ? null : getCurrentStack();
        };
        exports.cloneElement = function(element, config, children) {
          if (null === element || void 0 === element)
            throw Error(
              "The argument must be a React element, but you passed " + element + "."
            );
          var props = assign({}, element.props), key = element.key, owner = element._owner;
          if (null != config) {
            var JSCompiler_inline_result;
            a: {
              if (hasOwnProperty.call(config, "ref") && (JSCompiler_inline_result = Object.getOwnPropertyDescriptor(
                config,
                "ref"
              ).get) && JSCompiler_inline_result.isReactWarning) {
                JSCompiler_inline_result = false;
                break a;
              }
              JSCompiler_inline_result = void 0 !== config.ref;
            }
            JSCompiler_inline_result && (owner = getOwner());
            hasValidKey(config) && (checkKeyStringCoercion(config.key), key = "" + config.key);
            for (propName in config)
              !hasOwnProperty.call(config, propName) || "key" === propName || "__self" === propName || "__source" === propName || "ref" === propName && void 0 === config.ref || (props[propName] = config[propName]);
          }
          var propName = arguments.length - 2;
          if (1 === propName) props.children = children;
          else if (1 < propName) {
            JSCompiler_inline_result = Array(propName);
            for (var i = 0; i < propName; i++)
              JSCompiler_inline_result[i] = arguments[i + 2];
            props.children = JSCompiler_inline_result;
          }
          props = ReactElement(
            element.type,
            key,
            props,
            owner,
            element._debugStack,
            element._debugTask
          );
          for (key = 2; key < arguments.length; key++)
            validateChildKeys(arguments[key]);
          return props;
        };
        exports.createContext = function(defaultValue) {
          defaultValue = {
            $$typeof: REACT_CONTEXT_TYPE,
            _currentValue: defaultValue,
            _currentValue2: defaultValue,
            _threadCount: 0,
            Provider: null,
            Consumer: null
          };
          defaultValue.Provider = defaultValue;
          defaultValue.Consumer = {
            $$typeof: REACT_CONSUMER_TYPE,
            _context: defaultValue
          };
          defaultValue._currentRenderer = null;
          defaultValue._currentRenderer2 = null;
          return defaultValue;
        };
        exports.createElement = function(type, config, children) {
          for (var i = 2; i < arguments.length; i++)
            validateChildKeys(arguments[i]);
          i = {};
          var key = null;
          if (null != config)
            for (propName in didWarnAboutOldJSXRuntime || !("__self" in config) || "key" in config || (didWarnAboutOldJSXRuntime = true, console.warn(
              "Your app (or one of its dependencies) is using an outdated JSX transform. Update to the modern JSX transform for faster performance: https://react.dev/link/new-jsx-transform"
            )), hasValidKey(config) && (checkKeyStringCoercion(config.key), key = "" + config.key), config)
              hasOwnProperty.call(config, propName) && "key" !== propName && "__self" !== propName && "__source" !== propName && (i[propName] = config[propName]);
          var childrenLength = arguments.length - 2;
          if (1 === childrenLength) i.children = children;
          else if (1 < childrenLength) {
            for (var childArray = Array(childrenLength), _i = 0; _i < childrenLength; _i++)
              childArray[_i] = arguments[_i + 2];
            Object.freeze && Object.freeze(childArray);
            i.children = childArray;
          }
          if (type && type.defaultProps)
            for (propName in childrenLength = type.defaultProps, childrenLength)
              void 0 === i[propName] && (i[propName] = childrenLength[propName]);
          key && defineKeyPropWarningGetter(
            i,
            "function" === typeof type ? type.displayName || type.name || "Unknown" : type
          );
          var propName = 1e4 > ReactSharedInternals.recentlyCreatedOwnerStacks++;
          return ReactElement(
            type,
            key,
            i,
            getOwner(),
            propName ? Error("react-stack-top-frame") : unknownOwnerDebugStack,
            propName ? createTask(getTaskName(type)) : unknownOwnerDebugTask
          );
        };
        exports.createRef = function() {
          var refObject = { current: null };
          Object.seal(refObject);
          return refObject;
        };
        exports.forwardRef = function(render) {
          null != render && render.$$typeof === REACT_MEMO_TYPE ? console.error(
            "forwardRef requires a render function but received a `memo` component. Instead of forwardRef(memo(...)), use memo(forwardRef(...))."
          ) : "function" !== typeof render ? console.error(
            "forwardRef requires a render function but was given %s.",
            null === render ? "null" : typeof render
          ) : 0 !== render.length && 2 !== render.length && console.error(
            "forwardRef render functions accept exactly two parameters: props and ref. %s",
            1 === render.length ? "Did you forget to use the ref parameter?" : "Any additional parameter will be undefined."
          );
          null != render && null != render.defaultProps && console.error(
            "forwardRef render functions do not support defaultProps. Did you accidentally pass a React component?"
          );
          var elementType = { $$typeof: REACT_FORWARD_REF_TYPE, render }, ownName;
          Object.defineProperty(elementType, "displayName", {
            enumerable: false,
            configurable: true,
            get: function() {
              return ownName;
            },
            set: function(name) {
              ownName = name;
              render.name || render.displayName || (Object.defineProperty(render, "name", { value: name }), render.displayName = name);
            }
          });
          return elementType;
        };
        exports.isValidElement = isValidElement;
        exports.lazy = function(ctor) {
          ctor = { _status: -1, _result: ctor };
          var lazyType = {
            $$typeof: REACT_LAZY_TYPE,
            _payload: ctor,
            _init: lazyInitializer
          }, ioInfo = {
            name: "lazy",
            start: -1,
            end: -1,
            value: null,
            owner: null,
            debugStack: Error("react-stack-top-frame"),
            debugTask: console.createTask ? console.createTask("lazy()") : null
          };
          ctor._ioInfo = ioInfo;
          lazyType._debugInfo = [{ awaited: ioInfo }];
          return lazyType;
        };
        exports.memo = function(type, compare) {
          null == type && console.error(
            "memo: The first argument must be a component. Instead received: %s",
            null === type ? "null" : typeof type
          );
          compare = {
            $$typeof: REACT_MEMO_TYPE,
            type,
            compare: void 0 === compare ? null : compare
          };
          var ownName;
          Object.defineProperty(compare, "displayName", {
            enumerable: false,
            configurable: true,
            get: function() {
              return ownName;
            },
            set: function(name) {
              ownName = name;
              type.name || type.displayName || (Object.defineProperty(type, "name", { value: name }), type.displayName = name);
            }
          });
          return compare;
        };
        exports.startTransition = function(scope) {
          var prevTransition = ReactSharedInternals.T, currentTransition = {};
          currentTransition._updatedFibers = /* @__PURE__ */ new Set();
          ReactSharedInternals.T = currentTransition;
          try {
            var returnValue = scope(), onStartTransitionFinish = ReactSharedInternals.S;
            null !== onStartTransitionFinish && onStartTransitionFinish(currentTransition, returnValue);
            "object" === typeof returnValue && null !== returnValue && "function" === typeof returnValue.then && (ReactSharedInternals.asyncTransitions++, returnValue.then(releaseAsyncTransition, releaseAsyncTransition), returnValue.then(noop, reportGlobalError));
          } catch (error) {
            reportGlobalError(error);
          } finally {
            null === prevTransition && currentTransition._updatedFibers && (scope = currentTransition._updatedFibers.size, currentTransition._updatedFibers.clear(), 10 < scope && console.warn(
              "Detected a large number of updates inside startTransition. If this is due to a subscription please re-write it to use React provided hooks. Otherwise concurrent mode guarantees are off the table."
            )), null !== prevTransition && null !== currentTransition.types && (null !== prevTransition.types && prevTransition.types !== currentTransition.types && console.error(
              "We expected inner Transitions to have transferred the outer types set and that you cannot add to the outer Transition while inside the inner.This is a bug in React."
            ), prevTransition.types = currentTransition.types), ReactSharedInternals.T = prevTransition;
          }
        };
        exports.unstable_useCacheRefresh = function() {
          return resolveDispatcher().useCacheRefresh();
        };
        exports.use = function(usable) {
          return resolveDispatcher().use(usable);
        };
        exports.useActionState = function(action, initialState, permalink) {
          return resolveDispatcher().useActionState(
            action,
            initialState,
            permalink
          );
        };
        exports.useCallback = function(callback, deps) {
          return resolveDispatcher().useCallback(callback, deps);
        };
        exports.useContext = function(Context) {
          var dispatcher = resolveDispatcher();
          Context.$$typeof === REACT_CONSUMER_TYPE && console.error(
            "Calling useContext(Context.Consumer) is not supported and will cause bugs. Did you mean to call useContext(Context) instead?"
          );
          return dispatcher.useContext(Context);
        };
        exports.useDebugValue = function(value, formatterFn) {
          return resolveDispatcher().useDebugValue(value, formatterFn);
        };
        exports.useDeferredValue = function(value, initialValue) {
          return resolveDispatcher().useDeferredValue(value, initialValue);
        };
        exports.useEffect = function(create2, deps) {
          null == create2 && console.warn(
            "React Hook useEffect requires an effect callback. Did you forget to pass a callback to the hook?"
          );
          return resolveDispatcher().useEffect(create2, deps);
        };
        exports.useEffectEvent = function(callback) {
          return resolveDispatcher().useEffectEvent(callback);
        };
        exports.useId = function() {
          return resolveDispatcher().useId();
        };
        exports.useImperativeHandle = function(ref, create2, deps) {
          return resolveDispatcher().useImperativeHandle(ref, create2, deps);
        };
        exports.useInsertionEffect = function(create2, deps) {
          null == create2 && console.warn(
            "React Hook useInsertionEffect requires an effect callback. Did you forget to pass a callback to the hook?"
          );
          return resolveDispatcher().useInsertionEffect(create2, deps);
        };
        exports.useLayoutEffect = function(create2, deps) {
          null == create2 && console.warn(
            "React Hook useLayoutEffect requires an effect callback. Did you forget to pass a callback to the hook?"
          );
          return resolveDispatcher().useLayoutEffect(create2, deps);
        };
        exports.useMemo = function(create2, deps) {
          return resolveDispatcher().useMemo(create2, deps);
        };
        exports.useOptimistic = function(passthrough, reducer) {
          return resolveDispatcher().useOptimistic(passthrough, reducer);
        };
        exports.useReducer = function(reducer, initialArg, init) {
          return resolveDispatcher().useReducer(reducer, initialArg, init);
        };
        exports.useRef = function(initialValue) {
          return resolveDispatcher().useRef(initialValue);
        };
        exports.useState = function(initialState) {
          return resolveDispatcher().useState(initialState);
        };
        exports.useSyncExternalStore = function(subscribe, getSnapshot, getServerSnapshot) {
          return resolveDispatcher().useSyncExternalStore(
            subscribe,
            getSnapshot,
            getServerSnapshot
          );
        };
        exports.useTransition = function() {
          return resolveDispatcher().useTransition();
        };
        exports.version = "19.2.7";
        "undefined" !== typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ && "function" === typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStop && __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStop(Error());
      })();
    }
  });

  // node_modules/react/index.js
  var require_react = __commonJS({
    "node_modules/react/index.js"(exports, module) {
      "use strict";
      if (false) {
        module.exports = null;
      } else {
        module.exports = require_react_development();
      }
    }
  });

  // src/common.ts
  var common_exports = {};
  __export(common_exports, {
    Auth: () => Auth,
    Cart: () => Cart,
    NotificationSys: () => NotificationSys,
    useAuthStore: () => useAuthStore,
    useCartStore: () => useCartStore
  });

  // node_modules/zustand/esm/vanilla.mjs
  var createStoreImpl = (createState) => {
    let state;
    const listeners = /* @__PURE__ */ new Set();
    const setState = (partial, replace) => {
      const nextState = typeof partial === "function" ? partial(state) : partial;
      if (!Object.is(nextState, state)) {
        const previousState = state;
        state = (replace != null ? replace : typeof nextState !== "object" || nextState === null) ? nextState : Object.assign({}, state, nextState);
        listeners.forEach((listener) => listener(state, previousState));
      }
    };
    const getState = () => state;
    const getInitialState = () => initialState;
    const subscribe = (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    };
    const api = { setState, getState, getInitialState, subscribe };
    const initialState = state = createState(setState, getState, api);
    return api;
  };
  var createStore = ((createState) => createState ? createStoreImpl(createState) : createStoreImpl);

  // node_modules/zustand/esm/react.mjs
  var import_react = __toESM(require_react(), 1);
  var identity = (arg) => arg;
  function useStore(api, selector = identity) {
    const slice = import_react.default.useSyncExternalStore(
      api.subscribe,
      import_react.default.useCallback(() => selector(api.getState()), [api, selector]),
      () => selector(api.getInitialState())
    );
    import_react.default.useDebugValue(slice);
    return slice;
  }
  var createImpl = (createState) => {
    const api = createStore(createState);
    const useBoundStore = (selector) => useStore(api, selector);
    Object.assign(useBoundStore, api);
    return useBoundStore;
  };
  var create = ((createState) => createState ? createImpl(createState) : createImpl);

  // src/common.ts
  var useCartStore = create((set, get) => ({
    isOpen: false,
    pendingDeleteId: null,
    selectedItems: /* @__PURE__ */ new Set(),
    setIsOpen: (isOpen) => set({ isOpen }),
    setPendingDeleteId: (id) => set({ pendingDeleteId: id }),
    toggleSelection: (cartId, isChecked) => {
      const selectedItems = new Set(get().selectedItems);
      if (isChecked) selectedItems.add(cartId);
      else selectedItems.delete(cartId);
      set({ selectedItems });
    }
  }));
  var useAuthStore = create((set) => ({
    isLoginOpen: false,
    isRegisterOpen: false,
    isLogoutOpen: false,
    setLoginOpen: (open) => set({ isLoginOpen: open }),
    setRegisterOpen: (open) => set({ isRegisterOpen: open }),
    setLogoutOpen: (open) => set({ isLogoutOpen: open })
  }));
  window.formatPrice = (price) => {
    return new Intl.NumberFormat("zh-HK", { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(price);
  };
  window.showNotification = (message, isError = false) => {
    const notification = document.createElement("div");
    notification.style.cssText = `
        position: fixed; top: 50%; left: 50%; transform: translate(-50%, -60%);
        background: ${isError ? "#dc3545" : "var(--gold)"};
        color: ${isError ? "white" : "var(--primary)"};
        padding: 1.2rem 2.5rem; border-radius: 12px;
        box-shadow: 0 10px 40px rgba(0,0,0,0.25); z-index: 99999;
        opacity: 0; font-weight: 600; font-size: 1.1rem;
        display: flex; align-items: center; gap: 0.8rem;
        transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    `;
    notification.innerHTML = `<span>${message}</span>`;
    document.body.appendChild(notification);
    setTimeout(() => {
      notification.style.opacity = "1";
      notification.style.transform = "translate(-50%, -50%)";
    }, 10);
    setTimeout(() => {
      notification.style.opacity = "0";
      notification.style.transform = "translate(-50%, -70%)";
      setTimeout(() => notification.remove(), 400);
    }, 1500);
  };
  window.updateCartBadge = (count) => {
    const badge = document.getElementById("cartCountBadge");
    if (!badge) return;
    if (count > 0) {
      badge.textContent = count.toString();
      badge.style.display = "inline-flex";
    } else {
      badge.style.display = "none";
    }
  };
  async function addToCart(productId) {
    if (typeof window.isLoggedIn === "undefined" || !window.isLoggedIn) {
      window.showNotification("\u274C \u8ACB\u5148\u767B\u5165\u5F8C\u518D\u52A0\u5165\u8CFC\u7269\u8ECA\uFF01", true);
      return;
    }
    try {
      const response = await fetch(`/cart/api/add/${productId}`, { method: "POST" });
      const data = await response.json();
      if (response.ok) {
        window.showNotification("\u2705 \u5DF2\u52A0\u5165\u8CFC\u7269\u8ECA");
        Cart.loadItems();
      } else {
        window.showNotification("\u274C " + (data.message || "\u52A0\u5165\u5931\u6557"), true);
      }
    } catch (error) {
      console.error("\u52A0\u5165\u8CFC\u7269\u8ECA\u5931\u6557:", error);
      window.showNotification("\u274C \u7DB2\u7D61\u932F\u8AA4", true);
    }
  }
  window.addToCart = addToCart;
  var Cart = {
    toggleDropdown: (event) => {
      if (event) event.stopPropagation();
      const dropdown = document.getElementById("cartDropdown");
      if (!dropdown) return;
      const isOpen = !useCartStore.getState().isOpen;
      useCartStore.getState().setIsOpen(isOpen);
      if (isOpen) {
        dropdown.style.display = "flex";
        dropdown.style.flexDirection = "column";
        Cart.loadItems();
      } else {
        dropdown.style.display = "none";
      }
    },
    calculateSelectedTotal: () => {
      const checkboxes = document.querySelectorAll(".cart-item-select:checked");
      let total = 0;
      checkboxes.forEach((checkbox) => {
        const cartItem = checkbox.closest(".cart-item");
        if (cartItem) {
          const priceText = cartItem.querySelector(".cart-item-price")?.textContent?.replace("HK$ ", "").replace(/,/g, "") || "0";
          const quantity = parseInt(cartItem.querySelector(".qty-value")?.textContent || "1", 10);
          total += parseFloat(priceText) * quantity;
        }
      });
      const totalEl = document.getElementById("cartTotalAmount");
      if (totalEl) totalEl.textContent = "HK$ " + window.formatPrice(total);
    },
    loadItems: async () => {
      try {
        const response = await fetch("/cart/api/list");
        const data = await response.json();
        if (data.success) {
          Cart.renderItems(data.cartItems, data.totalAmount);
          window.updateCartBadge(data.cartCount);
          useCartStore.getState().selectedItems.clear();
          data.cartItems.forEach((item) => {
            if (item.selected !== false) useCartStore.getState().selectedItems.add(item.cartId);
          });
          Cart.calculateSelectedTotal();
        }
      } catch (error) {
        console.error("\u52A0\u8F09\u8CFC\u7269\u8ECA\u5931\u6557:", error);
      }
    },
    renderItems: (cartItems, totalAmount) => {
      const container = document.getElementById("cartItemsContainer");
      if (!container || !container.closest(".cart-dropdown-menu")) {
        console.warn("\u{1F6E1}\uFE0F [\u5B89\u5168\u6514\u622A] \u963B\u6B62\u4E86 renderCartItems \u8986\u84CB\u8A73\u60C5\u9801 DOM\uFF01");
        return;
      }
      if (!container) return;
      useCartStore.getState().selectedItems.clear();
      if (!cartItems || cartItems.length === 0) {
        container.innerHTML = `<div class="cart-empty"><i class="fas fa-shopping-basket" style="font-size: 3rem; color: #ddd; margin-bottom: 1rem;"></i><p>\u8CFC\u7269\u8ECA\u662F\u7A7A\u7684</p></div>`;
        const totalEl2 = document.getElementById("cartTotalAmount");
        if (totalEl2) totalEl2.textContent = "HK$ 0";
        return;
      }
      let html = "";
      cartItems.forEach((item) => {
        const isChecked = item.selected !== false;
        if (isChecked) useCartStore.getState().selectedItems.add(item.cartId);
        html += `<div class="cart-item" data-cart-id="${item.cartId}" data-product-id="${item.product.productId}">
                <div class="cart-item-checkbox">
                    <input type="checkbox" class="cart-item-select" data-cart-id="${item.cartId}" 
                           onchange="window.Cart.toggleSelection(${item.cartId}, this.checked)" ${isChecked ? "checked" : ""}>
                </div>
                <div class="cart-item-img"><img src="/images/products/${item.product.image}" alt="${item.product.description}" onerror="this.src='/images/placeholder.png'"></div>
                <div class="cart-item-info">
                    <div class="cart-item-name">${item.product.description}</div>
                    <div class="cart-item-price">HK$ ${window.formatPrice(item.price)}</div>
                    <div class="cart-item-quantity">
                        <button class="qty-btn" onclick="window.Cart.updateQuantity(${item.cartId}, ${item.quantity - 1})" ${item.quantity <= 1 ? "disabled" : ""}>-</button>
                        <span class="qty-value">${item.quantity}</span>
                        <button class="qty-btn" onclick="window.Cart.updateQuantity(${item.cartId}, ${item.quantity + 1})" ${item.quantity >= item.product.stock ? "disabled" : ""}>+</button>
                    </div>
                </div>
                <div class="cart-item-remove" onclick="window.Cart.removeFromCart(${item.product.productId})"><i class="fas fa-trash-alt"></i></div>
            </div>`;
      });
      container.innerHTML = html;
      const totalEl = document.getElementById("cartTotalAmount");
      if (totalEl) totalEl.textContent = "HK$ " + window.formatPrice(totalAmount);
    },
    toggleSelection: async (cartId, isChecked) => {
      useCartStore.getState().toggleSelection(cartId, isChecked);
      try {
        await fetch(`/cart/api/toggle-selection/${cartId}?isSelected=${isChecked}`, { method: "PUT" });
        if (window.location.pathname === "/cart/view") {
          const detailCheckbox = document.querySelector(`.cart-items-list input[data-cart-id="${cartId}"]`);
          if (detailCheckbox) {
            detailCheckbox.checked = isChecked;
            const cartItem = detailCheckbox.closest(".cart-item");
            if (isChecked) cartItem?.classList.remove("not-selected");
            else cartItem?.classList.add("not-selected");
            if (typeof window.updateCartTotal === "function") window.updateCartTotal();
            if (typeof window.updateSelectedCount === "function") window.updateSelectedCount();
            if (typeof window.syncSelectAllCheckbox === "function") window.syncSelectAllCheckbox();
          }
        }
      } catch (error) {
        console.error("\u540C\u6B65\u9078\u4E2D\u72C0\u614B\u5931\u6557:", error);
        useCartStore.getState().toggleSelection(cartId, !isChecked);
        if (window.location.pathname === "/cart/view") {
          const detailCheckbox = document.querySelector(`.cart-items-list input[data-cart-id="${cartId}"]`);
          if (detailCheckbox) {
            detailCheckbox.checked = !isChecked;
            const cartItem = detailCheckbox.closest(".cart-item");
            if (isChecked) cartItem?.classList.add("not-selected");
            else cartItem?.classList.remove("not-selected");
            if (typeof window.updateCartTotal === "function") window.updateCartTotal();
            if (typeof window.updateSelectedCount === "function") window.updateSelectedCount();
            if (typeof window.syncSelectAllCheckbox === "function") window.syncSelectAllCheckbox();
          }
        }
        window.showNotification("\u274C \u7DB2\u7D61\u932F\u8AA4\uFF0C\u540C\u6B65\u5931\u6557", true);
      }
      Cart.calculateSelectedTotal();
    },
    updateQuantity: async (cartId, newQuantity) => {
      if (newQuantity <= 0) return;
      const navQtyElement = document.querySelector(`#cartItemsContainer [data-cart-id="${cartId}"] .qty-value`);
      const originalNavQty = navQtyElement ? parseInt(navQtyElement.textContent || "1", 10) : newQuantity;
      const detailQtyElement = document.querySelector(`.cart-items-list [data-cart-id="${cartId}"] .qty-value`);
      const originalDetailQty = detailQtyElement ? parseInt(detailQtyElement.textContent || "1", 10) : newQuantity;
      if (navQtyElement) navQtyElement.textContent = newQuantity.toString();
      if (detailQtyElement) detailQtyElement.textContent = newQuantity.toString();
      if (detailQtyElement) {
        const detailCartItem = detailQtyElement.closest(".cart-item");
        const price = parseFloat(detailCartItem?.dataset.price || "0");
        const subtotalSpan = detailCartItem?.querySelector(".item-subtotal span");
        if (subtotalSpan) subtotalSpan.textContent = (price * newQuantity).toLocaleString("zh-HK");
        if (typeof window.updateCartTotal === "function") window.updateCartTotal();
        if (typeof window.updateSelectedCount === "function") window.updateSelectedCount();
      }
      try {
        const response = await fetch(`/cart/api/update/${cartId}?quantity=${newQuantity}`, { method: "PUT" });
        const data = await response.json();
        if (response.ok) {
          await Cart.loadItems();
          window.showNotification("\u2705 \u6578\u91CF\u66F4\u65B0\u6210\u529F");
        } else {
          if (navQtyElement) navQtyElement.textContent = originalNavQty.toString();
          if (detailQtyElement) detailQtyElement.textContent = originalDetailQty.toString();
          if (detailQtyElement) {
            const detailCartItem = detailQtyElement.closest(".cart-item");
            const price = parseFloat(detailCartItem?.dataset.price || "0");
            const subtotalSpan = detailCartItem?.querySelector(".item-subtotal span");
            if (subtotalSpan) subtotalSpan.textContent = (price * originalDetailQty).toLocaleString("zh-HK");
            if (typeof window.updateCartTotal === "function") window.updateCartTotal();
            if (typeof window.updateSelectedCount === "function") window.updateSelectedCount();
          }
          window.showNotification("\u274C " + (data.message || "\u66F4\u65B0\u5931\u6557"), true);
        }
      } catch (error) {
        console.error("\u66F4\u65B0\u8CFC\u7269\u8ECA\u5931\u6557:", error);
        if (navQtyElement) navQtyElement.textContent = originalNavQty.toString();
        if (detailQtyElement) detailQtyElement.textContent = originalDetailQty.toString();
        if (detailQtyElement) {
          const detailCartItem = detailQtyElement.closest(".cart-item");
          const price = parseFloat(detailCartItem?.dataset.price || "0");
          const subtotalSpan = detailCartItem?.querySelector(".item-subtotal span");
          if (subtotalSpan) subtotalSpan.textContent = (price * originalDetailQty).toLocaleString("zh-HK");
          if (typeof window.updateCartTotal === "function") window.updateCartTotal();
          if (typeof window.updateSelectedCount === "function") window.updateSelectedCount();
        }
        window.showNotification("\u274C \u7DB2\u7D61\u932F\u8AA4\uFF0C\u540C\u6B65\u5931\u6557", true);
      }
    },
    removeFromCart: (productId) => {
      useCartStore.getState().setPendingDeleteId(productId);
      const modal = document.getElementById("cartDeleteModal");
      if (modal) {
        modal.style.display = "flex";
        document.body.style.overflow = "hidden";
      }
    },
    closeDeleteModal: () => {
      const modal = document.getElementById("cartDeleteModal");
      if (modal) {
        modal.style.display = "none";
        document.body.style.overflow = "";
      }
      useCartStore.getState().setPendingDeleteId(null);
    },
    confirmDelete: async () => {
      const pendingDeleteId = useCartStore.getState().pendingDeleteId;
      if (!pendingDeleteId) return;
      const detailCartItem = document.querySelector(`.cart-items-list [data-product-id="${pendingDeleteId}"]`);
      try {
        const response = await fetch(`/cart/api/remove/${pendingDeleteId}`, { method: "DELETE" });
        if (response.ok) {
          Cart.closeDeleteModal();
          window.showNotification("\u2705 \u5546\u54C1\u5DF2\u79FB\u9664");
          await Cart.loadItems();
          if (window.location.pathname === "/cart/view" && detailCartItem) {
            detailCartItem.style.transition = "all 0.3s ease";
            detailCartItem.style.opacity = "0";
            detailCartItem.style.transform = "translateX(-20px)";
            setTimeout(() => {
              detailCartItem?.remove();
              const remainingItems = document.querySelectorAll(".cart-items-list .cart-item");
              if (remainingItems.length === 0) {
                location.reload();
              } else {
                if (typeof window.updateCartTotal === "function") window.updateCartTotal();
                if (typeof window.updateSelectedCount === "function") window.updateSelectedCount();
                if (typeof window.syncSelectAllCheckbox === "function") window.syncSelectAllCheckbox();
              }
            }, 300);
          }
        } else {
          const data = await response.json();
          window.showNotification("\u274C " + (data.message || "\u79FB\u9664\u5931\u6557"), true);
        }
      } catch (error) {
        console.error("\u79FB\u9664\u5546\u54C1\u5931\u6557:", error);
        window.showNotification("\u274C \u7DB2\u7D61\u932F\u8AA4\uFF0C\u540C\u6B65\u5931\u6557", true);
        Cart.closeDeleteModal();
      }
    },
    checkout: async () => {
      if (!window.isLoggedIn) {
        window.showNotification("\u274C \u8ACB\u5148\u767B\u5165\uFF01", true);
        return;
      }
      if (useCartStore.getState().selectedItems.size === 0) {
        window.showNotification("\u274C \u8ACB\u81F3\u5C11\u9078\u64C7\u4E00\u4EF6\u5546\u54C1\uFF01", true);
        return;
      }
      try {
        const response = await fetch("/checkout/api/create", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ selectedCartIds: Array.from(useCartStore.getState().selectedItems) })
        });
        const result = await response.json();
        if (response.ok && result.success) {
          window.location.href = `/checkout?orderNo=${result.data}`;
        } else {
          window.showNotification("\u274C " + (result.message || "\u5275\u5EFA\u8A02\u55AE\u5931\u6557"), true);
        }
      } catch (error) {
        console.error("\u7D50\u8CEC\u932F\u8AA4:", error);
        window.showNotification("\u274C \u7DB2\u7D61\u932F\u8AA4", true);
      }
    }
  };
  window.Cart = Cart;
  var Auth = {
    openRegisterModal: () => {
      useAuthStore.getState().setRegisterOpen(true);
      const modal = document.getElementById("registerModal");
      if (modal) {
        modal.style.display = "flex";
        document.body.style.overflow = "hidden";
        const form = document.getElementById("registerForm");
        if (form) form.reset();
      }
    },
    closeRegisterModal: () => {
      useAuthStore.getState().setRegisterOpen(false);
      const modal = document.getElementById("registerModal");
      if (modal) {
        modal.style.display = "none";
        document.body.style.overflow = "";
      }
    },
    openLoginModal: () => {
      useAuthStore.getState().setLoginOpen(true);
      const modal = document.getElementById("loginModal");
      if (modal) {
        modal.style.display = "flex";
        document.body.style.overflow = "hidden";
        const form = document.getElementById("loginForm");
        if (form) form.reset();
      }
    },
    closeLoginModal: () => {
      useAuthStore.getState().setLoginOpen(false);
      const modal = document.getElementById("loginModal");
      if (modal) {
        modal.style.display = "none";
        document.body.style.overflow = "";
      }
    },
    switchToRegister: () => {
      Auth.closeLoginModal();
      setTimeout(Auth.openRegisterModal, 200);
    },
    switchToLogin: () => {
      Auth.closeRegisterModal();
      setTimeout(Auth.openLoginModal, 200);
    },
    togglePassword: (inputId, icon) => {
      const input = document.getElementById(inputId);
      if (!input) return;
      if (input.type === "password") {
        input.type = "text";
        icon.classList.replace("fa-eye", "fa-eye-slash");
      } else {
        input.type = "password";
        icon.classList.replace("fa-eye-slash", "fa-eye");
      }
    },
    handleRegister: async (e) => {
      e.preventDefault();
      const form = e.target;
      const msg = document.getElementById("registerMsg");
      if (!msg) return;
      if (form.elements.namedItem("password").value !== form.elements.namedItem("confirmPassword").value) {
        msg.textContent = "\u274C \u5169\u6B21\u5BC6\u78BC\u8F38\u5165\u4E0D\u4E00\u81F4";
        msg.style.color = "var(--accent)";
        form.elements.namedItem("confirmPassword").focus();
        return;
      }
      const addressInput = document.getElementById("regAddress");
      const fullAddress = addressInput ? addressInput.value.trim() : "";
      if (fullAddress && fullAddress.length < 3) {
        msg.textContent = "\u274C \u8A73\u7D30\u5730\u5740\u4E0D\u80FD\u70BA\u7A7A\uFF01";
        msg.style.color = "var(--accent)";
        if (addressInput) {
          addressInput.focus();
          addressInput.style.borderColor = "#e94560";
        }
        return;
      }
      msg.textContent = "";
      try {
        const formData = {
          username: form.elements.namedItem("username").value.trim(),
          name: form.elements.namedItem("name").value.trim(),
          email: form.elements.namedItem("email").value.trim(),
          password: form.elements.namedItem("password").value,
          phone: form.elements.namedItem("phone").value.trim(),
          fullAddress
        };
        const response = await fetch("/api/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(formData)
        });
        const result = await response.json();
        if (response.ok) {
          msg.textContent = "\u2705 \u8A3B\u518A\u6210\u529F\uFF013\u79D2\u5F8C\u81EA\u52D5\u767B\u5165...";
          msg.style.color = "green";
          setTimeout(() => {
            Auth.closeRegisterModal();
            Auth.openLoginModal();
            const loginForm = document.getElementById("loginForm");
            if (loginForm) {
              loginForm.elements.namedItem("username").value = formData.username;
              loginForm.elements.namedItem("password").focus();
            }
          }, 2500);
        } else {
          msg.textContent = "\u274C " + (result.message || "\u8A3B\u518A\u5931\u6557\uFF0C\u8ACB\u91CD\u8A66");
          msg.style.color = "var(--accent)";
        }
      } catch (error) {
        msg.textContent = "\u274C \u7DB2\u7D61\u932F\u8AA4\uFF0C\u8ACB\u6AA2\u67E5\u9023\u63A5";
        msg.style.color = "var(--accent)";
        console.error("\u8A3B\u518A\u932F\u8AA4:", error);
      }
    },
    handleLogin: async (e) => {
      e.preventDefault();
      const form = e.target;
      const msg = document.getElementById("loginMsg");
      if (!msg) return;
      msg.textContent = "";
      msg.style.color = "var(--accent)";
      try {
        const response = await fetch("/api/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: form.elements.namedItem("username").value.trim(),
            password: form.elements.namedItem("password").value
          })
        });
        const result = await response.json();
        if (response.ok) {
          msg.textContent = "\u2705 \u767B\u5165\u6210\u529F\uFF01\u6B63\u5728\u5237\u65B0...";
          msg.style.color = "green";
          setTimeout(() => {
            Auth.closeLoginModal();
            window.location.reload();
          }, 1200);
        } else {
          let errorMsg = result.message || "\u7528\u6236\u540D\u6216\u5BC6\u78BC\u932F\u8AA4";
          if (errorMsg.startsWith("USER_NOT_FOUND:")) {
            errorMsg = "\u274C \u8A72\u7528\u6236\u540D\u4E0D\u5B58\u5728";
            form.querySelector('input[name="username"]').classList.add("shake");
            setTimeout(() => form.querySelector('input[name="username"]').classList.remove("shake"), 500);
          } else if (errorMsg.startsWith("INVALID_PASSWORD:")) {
            errorMsg = "\u274C \u8F38\u5165\u5BC6\u78BC\u4E0D\u6B63\u78BA";
            form.querySelector('input[name="password"]').classList.add("shake");
            setTimeout(() => form.querySelector('input[name="password"]').classList.remove("shake"), 500);
          } else {
            errorMsg = "\u274C " + errorMsg;
          }
          msg.textContent = errorMsg;
        }
      } catch (error) {
        msg.textContent = "\u274C \u7DB2\u7D61\u932F\u8AA4\uFF0C\u8ACB\u6AA2\u67E5\u9023\u63A5";
      }
    },
    showLogoutModal: () => {
      useAuthStore.getState().setLogoutOpen(true);
      const modal = document.getElementById("logoutModal");
      if (modal) {
        modal.style.display = "flex";
        document.body.style.overflow = "hidden";
      }
    },
    closeLogoutModal: () => {
      useAuthStore.getState().setLogoutOpen(false);
      const modal = document.getElementById("logoutModal");
      if (modal) {
        modal.style.display = "none";
        document.body.style.overflow = "";
      }
    },
    performLogout: async () => {
      Auth.closeLogoutModal();
      try {
        await fetch("/logout", { method: "POST", headers: { "Content-Type": "application/json" } });
        window.location.href = "/";
      } catch (error) {
        console.error("\u767B\u51FA\u5931\u6557:", error);
        window.location.href = "/";
      }
    },
    handleLogout: () => {
      Auth.showLogoutModal();
    },
    requireLogin: (url, message) => {
      if (typeof window.isLoggedIn === "undefined" || !window.isLoggedIn) {
        window.showNotification(message || "\u274C \u8ACB\u5148\u767B\u5165\uFF01", true);
        setTimeout(() => {
          Auth.openLoginModal();
        }, 500);
        return false;
      }
      window.location.href = url;
      return true;
    }
  };
  window.Auth = Auth;
  var NotificationSys = {
    fetchUnreadCounts: async () => {
      try {
        const response = await fetch("/api/notifications/unread-count");
        const data = await response.json();
        const msgBadge = document.getElementById("notificationBadge");
        if (msgBadge) {
          if (data.messageCount > 0) {
            msgBadge.textContent = data.messageCount > 99 ? "99+" : data.messageCount.toString();
            msgBadge.style.display = "inline-flex";
          } else {
            msgBadge.style.display = "none";
          }
        }
        const sysBadge = document.getElementById("systemNotificationBadge");
        if (sysBadge) {
          if (data.systemCount > 0) {
            sysBadge.textContent = data.systemCount > 99 ? "99+" : data.systemCount.toString();
            sysBadge.style.display = "inline-flex";
          } else {
            sysBadge.style.display = "none";
          }
        }
        let targetTab = "MY";
        if (data.replyCount > 0) targetTab = "REPLY";
        else if (data.mentionCount > 0) targetTab = "MENTION";
        else if (data.likeCount > 0) targetTab = "LIKED_ME";
        const navLinks = document.querySelectorAll('a[href^="/account/reviews"]');
        navLinks.forEach((link) => {
          if (data.messageCount > 0) {
            link.href = `/account/reviews?type=${targetTab}`;
          } else {
            link.href = `/account/reviews`;
          }
        });
      } catch (error) {
        console.error("\u7372\u53D6\u901A\u77E5\u5931\u6557:", error);
      }
    }
  };
  window.NotificationSys = NotificationSys;
  var RegionCascade = {
    init: () => {
      const regionSelect = document.getElementById("regionSelect");
      const districtSelect = document.getElementById("districtSelect");
      const addressInput = document.getElementById("regAddress");
      if (!regionSelect || !districtSelect || !addressInput) return;
      const districtData = {
        \u6E2F\u5C9B: ["\u4E2D\u897F\u533A", "\u6E7E\u4ED4\u533A", "\u4E1C\u533A", "\u5357\u533A"],
        \u4E5D\u9F99: ["\u6CB9\u5C16\u65FA\u533A", "\u6DF1\u6C34\u57D7\u533A", "\u4E5D\u9F99\u57CE\u533A", "\u9EC4\u5927\u4ED9\u533A", "\u89C2\u5858\u533A"],
        \u65B0\u754C: ["\u8343\u6E7E\u533A", "\u5C6F\u95E8\u533A", "\u5143\u6717\u533A", "\u5317\u533A", "\u5927\u57D4\u533A", "\u897F\u8D21\u533A", "\u6C99\u7530\u533A", "\u8475\u9752\u533A", "\u79BB\u5C9B\u533A"]
      };
      regionSelect.addEventListener("change", function() {
        const selectedRegion = this.value;
        districtSelect.innerHTML = '<option value="">\u8BF7\u9009\u62E9\u884C\u653F\u533A</option>';
        districtSelect.disabled = true;
        districtSelect.style.backgroundColor = "#f8f9fa";
        if (selectedRegion && districtData[selectedRegion]) {
          districtSelect.disabled = false;
          districtSelect.style.backgroundColor = "white";
          districtData[selectedRegion].forEach((district) => {
            const option = document.createElement("option");
            option.value = district;
            option.textContent = district;
            districtSelect.appendChild(option);
          });
        }
      });
      districtSelect.addEventListener("change", function() {
        const region = regionSelect.value;
        const district = this.value;
        if (region && district) {
          if (!addressInput.value.startsWith(region)) {
            addressInput.value = region + " " + district + " ";
          } else {
            if (addressInput.value.trim() === "") {
              addressInput.value = region + " " + district + " ";
            }
          }
          addressInput.focus();
          addressInput.classList.remove("is-invalid");
        }
      });
      const registerForm = document.getElementById("registerForm");
      if (registerForm) {
        registerForm.addEventListener("submit", function(e) {
          const region = regionSelect.value;
          const address = addressInput.value.trim();
          if (region && (!address || !address.includes(region))) {
            e.preventDefault();
            window.showNotification("\u274C \u65E2\u7136\u9078\u64C7\u4E86\u5340\u57DF\uFF0C\u8A73\u7D30\u5730\u5740\u4E0D\u80FD\u70BA\u7A7A\uFF0C\u4E14\u5FC5\u9808\u5305\u542B\u6240\u9078\u5340\u57DF\uFF01", true);
            addressInput.focus();
            addressInput.style.borderColor = "#e94560";
          }
        });
      }
    }
  };
  async function loadScrollingNotification() {
    try {
      const response = await fetch("/api/config/notification");
      const config = await response.json();
      const container = document.getElementById("notificationContainer");
      const notificationText = document.getElementById("scrollingNotification");
      if (!container || !notificationText) return;
      if (!config.notificationText || config.notificationText.trim() === "") {
        container.style.display = "none";
        return;
      }
      const text = config.notificationText;
      const scrollEnabled = config.scrollEnabled !== "false";
      const direction = config.scrollDirection || "rtl";
      notificationText.innerHTML = "";
      notificationText.style.whiteSpace = "nowrap";
      if (scrollEnabled && direction === "ltr") {
        const chars = text.split("").reverse();
        for (const char of chars) {
          const span = document.createElement("span");
          span.textContent = char;
          notificationText.appendChild(span);
        }
      } else {
        for (const char of text) {
          const span = document.createElement("span");
          span.textContent = char;
          notificationText.appendChild(span);
        }
      }
      notificationText.style.color = config.textColor || "#FFFFFF";
      notificationText.style.fontWeight = config.fontWeight || "normal";
      notificationText.style.fontSize = (config.fontSize || "14") + "px";
      notificationText.style.fontStyle = config.fontItalic === "true" ? "italic" : "normal";
      if (scrollEnabled) {
        notificationText.classList.remove("is-scrolling-rtl", "is-scrolling-ltr");
        const speed = config.scrollSpeed || "normal";
        const intervalSeconds = parseFloat(config.scrollInterval) || 0;
        let secondsPerChar = 1.4;
        if (speed === "slow") secondsPerChar = 1.7;
        else if (speed === "fast") secondsPerChar = 1.2;
        const totalChars = text.length;
        const pureScrollDuration = totalChars * secondsPerChar;
        const totalCycleDuration = pureScrollDuration + intervalSeconds;
        const finalDuration = Math.max(5, totalCycleDuration);
        if (direction === "ltr") {
          notificationText.classList.add("is-scrolling-ltr");
          notificationText.style.animationDuration = `${finalDuration}s`;
        } else {
          notificationText.classList.add("is-scrolling-rtl");
          notificationText.style.animationDuration = `${finalDuration}s`;
        }
      } else {
        notificationText.classList.remove("is-scrolling-rtl", "is-scrolling-ltr");
        notificationText.style.animation = "none";
      }
      container.style.display = "flex";
    } catch (error) {
      console.error("\u52A0\u8F09\u6EFE\u52D5\u901A\u77E5\u5931\u6557:", error);
    }
  }
  document.addEventListener("DOMContentLoaded", () => {
    RegionCascade.init();
    loadScrollingNotification();
    const loginModal = document.getElementById("loginModal");
    if (loginModal) {
      loginModal.addEventListener("click", (e) => {
        if (e.target === loginModal) Auth.closeLoginModal();
      });
    }
    const registerModal = document.getElementById("registerModal");
    if (registerModal) {
      registerModal.addEventListener("click", (e) => {
        if (e.target === registerModal) Auth.closeRegisterModal();
      });
    }
    if (document.querySelector(".nav-dropdown") && typeof Cart !== "undefined") {
      Cart.loadItems();
    }
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        Auth.closeRegisterModal();
        Auth.closeLoginModal();
        Auth.closeLogoutModal();
        Cart.closeDeleteModal();
      }
    });
    if (typeof window.isLoggedIn !== "undefined" && window.isLoggedIn) {
      NotificationSys.fetchUnreadCounts();
    }
  });
  return __toCommonJS(common_exports);
})();
/*! Bundled license information:

react/cjs/react.development.js:
  (**
   * @license React
   * react.development.js
   *
   * Copyright (c) Meta Platforms, Inc. and affiliates.
   *
   * This source code is licensed under the MIT license found in the
   * LICENSE file in the root directory of this source tree.
   *)
*/
