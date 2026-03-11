# Events

A lightweight and flexible event bus for Java applications.

Events provides a complete annotation-driven event dispatch system with priority-ordered handler execution, cancellable events, hierarchical event dispatch, asynchronous event support, and reflective listener registration.

The library is designed to be lightweight, fast, and easy to integrate into existing Java applications.

---

## Features

- Annotation-driven handler registration via `@EventHandler`
- Integer-based priority constants with `Integer.MIN_VALUE`/`MAX_VALUE` boundaries for guaranteed ordering
- Cancellable events with per-handler `ignoreCancelled` filtering and cancellation reasons
- Convenience base classes — `CancellableEvent`, `AsyncEvent`, `CancellableAsyncEvent`
- Hierarchical event dispatch — parent events auto-fire registered child events
- Child-to-parent propagation — calling a child also invokes parent handlers
- `@AsynchronousEvent` annotation with `@Inherited` support for event hierarchies
- Automatic async dispatch via configurable `ExecutorService` with daemon thread pool
- Sync/async enforcement — `EventApi` rejects mismatched dispatch calls at runtime
- Per-event-type `HandlerList` with baked arrays for zero-allocation dispatch
- Thread-safe handler storage with private object locking
- `EventApi` static class for convenient access without managing an `EventBus` instance
- Minimal dependencies
- Designed for modern Java (Java 21+)

---

## Requirements

Events has no external runtime dependencies.

The following is only needed at compile time for annotation processing:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.36</version>
    <scope>provided</scope>
</dependency>
```

---

## Installation

Add the dependency to your Maven project:
```xml
<dependency>
    <groupId>io.github.trae</groupId>
    <artifactId>events</artifactId>
    <version>0.0.1</version>
</dependency>
```

---

## Quick Start

Define events, register listeners, and dispatch with a single static call.
```java
@AllArgsConstructor
@Getter
public abstract class AccountEvent extends Event {

    private final UUID id;
}

@Getter
public class AccountCreateEvent extends AccountEvent {

    private final String email;

    public AccountCreateEvent() {
        this(UUID.randomUUID(), "unknown");
    }

    public AccountCreateEvent(UUID id, String email) {
        super(id);

        this.email = email;
    }
}

public class AccountListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAccountCreate(AccountCreateEvent event) {
        System.out.println("Account created: " + event.getId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnyAccountEvent(AccountEvent event) {
        System.out.println("Account activity: " + event.getId());
    }
}

// Register and dispatch
EventApi.registerListener(new AccountListener());
EventApi.dispatchEvent(new AccountCreateEvent(UUID.randomUUID(), "john@example.com"));
```

---

## Defining Events

Every event extends `Event` and adds its own fields:
```java
public class AccountCreateEvent extends Event {}
```

For cancellable events, extend `CancellableEvent`:
```java
public class AccountSendMessageEvent extends CancellableEvent {}
```

For asynchronous events, extend `AsyncEvent`:
```java
public class AccountNotificationAsyncEvent extends AsyncEvent {}
```

For events that are both cancellable and asynchronous, extend `CancellableAsyncEvent`:
```java
public class AccountTransferAsyncEvent extends CancellableAsyncEvent {}
```

---

## Event Hierarchies

Parent events automatically dispatch to registered child events. Child events must provide a no-arg constructor for auto-instantiation:
```java
@AllArgsConstructor
@Getter
public abstract class AccountEvent extends Event {

    private final UUID id;
}

@Getter
public class AccountCreateEvent extends AccountEvent {

    private final String email;

    public AccountCreateEvent() {
        this(UUID.randomUUID(), "unknown");
    }

    public AccountCreateEvent(UUID id, String email) {
        super(id);

        this.email = email;
    }
}
```

Calling `EventApi.dispatchEvent(new AccountEvent(uuid))` will fire `AccountEvent` handlers first, then auto-dispatch `AccountCreateEvent` and any other registered children via their no-arg constructors.

Calling `EventApi.dispatchEvent(new AccountCreateEvent(uuid, "john@example.com"))` will fire `AccountCreateEvent` handlers first, then propagate up to `AccountEvent` handlers with the same event instance.

---

## Asynchronous Events

Events that extend `AsyncEvent` or `CancellableAsyncEvent` are automatically dispatched on a separate thread pool. The `@AsynchronousEvent` annotation is `@Inherited`, so child classes inherit it from annotated parents:
```java
public abstract class AccountAsyncEvent extends AsyncEvent {}

// AccountCreateAsyncEvent is also asynchronous — inherited from AsyncEvent
public class AccountCreateAsyncEvent extends AccountAsyncEvent {}
```

Synchronous and asynchronous dispatch are strictly enforced by `EventApi`. Calling `dispatchEvent()` on an async event or `dispatchAsynchronousEvent()` on a sync event will throw an `EventException`.

A custom `ExecutorService` can be provided via `EventApi.setAsynchronousExecutorService()`.

---

## Defining Listeners

Implement `Listener` and annotate handler methods with `@EventHandler`. Each method must accept exactly one parameter that extends `Event`:
```java
public class AccountListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAccountCreate(AccountCreateEvent event) {
        if (this.isBlacklisted(event.getEmail())) {
            event.setCancelledWithReason("Blacklisted email domain");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnyAccountEvent(AccountEvent event) {
        System.out.println("Account activity: " + event.getId());
    }
}
```

---

## EventApi

Static class for convenient access without managing an `EventBus` instance directly:
```java
EventApi.registerListener(new AccountListener());

// Fire-and-forget (synchronous)
EventApi.dispatchEvent(new ApplicationStartEvent());

// Fire-and-forget (asynchronous — event must extend AsyncEvent or be annotated with @AsynchronousEvent)
EventApi.dispatchAsynchronousEvent(new AccountNotificationAsyncEvent());

// Dispatch and get the event back (synchronous)
AccountCreateEvent event = EventApi.supplyEvent(new AccountCreateEvent(uuid, "john@example.com"));

// Dispatch and get a future back (asynchronous)
CompletableFuture<AccountNotificationAsyncEvent> future = EventApi.supplyAsynchronousEvent(new AccountNotificationAsyncEvent());

// State checks
EventApi.isAsynchronousEvent(AccountNotificationAsyncEvent.class);
EventApi.isCancellableEvent(event);
EventApi.isEventCancelled(event);
```

---

## Cancellation

Events implementing `Cancellable` (or extending `CancellableEvent` / `CancellableAsyncEvent`) can be cancelled by any handler. Use `ignoreCancelled` to skip handlers when the event is already cancelled:
```java
public class AccountListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAccountCreate(AccountCreateEvent event) {
        if (this.isBlacklisted(event.getEmail())) {
            event.setCancelledWithReason("Blacklisted email domain");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAccountCreateFinalize(AccountCreateEvent event) {
        if (event.isCancelled()) {
            return;
        }

        // Proceed with account creation
        this.accountService.create(event.getId(), event.getEmail());
    }
}
```

---

## Priority Order

Handlers execute in ascending priority order. `BASELINE` and `MONITOR` should only observe, never modify:

| Constant | Value | Purpose |
|---|---|---|
| `EventPriority.BASELINE` | `Integer.MIN_VALUE` | Runs first — observation and initial setup only |
| `EventPriority.LOWEST` | `Integer.MIN_VALUE + 1` | For handlers that need to run before most others |
| `EventPriority.LOW` | `Integer.MIN_VALUE + 2` | Early processing |
| `EventPriority.NORMAL` | `0` | Default — standard handler logic |
| `EventPriority.HIGH` | `Integer.MAX_VALUE - 2` | Late processing — validation, filtering |
| `EventPriority.HIGHEST` | `Integer.MAX_VALUE - 1` | Final say on cancellation |
| `EventPriority.MONITOR` | `Integer.MAX_VALUE` | Runs last — logging and auditing only |

Custom integer values are also supported for fine-grained ordering between the built-in constants.
