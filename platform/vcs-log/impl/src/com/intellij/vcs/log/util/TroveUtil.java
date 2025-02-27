// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConsumer;
import gnu.trove.*;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class TroveUtil {
  @NotNull
  public static <T> Stream<T> streamValues(@NotNull TIntObjectHashMap<? extends T> map) {
    TIntObjectIterator<? extends T> it = map.iterator();
    return Stream.<T>generate(() -> {
      it.advance();
      return it.value();
    }).limit(map.size());
  }

  @NotNull
  public static IntStream streamKeys(@NotNull TIntObjectHashMap<?> map) {
    TIntObjectIterator<?> it = map.iterator();
    return IntStream.generate(() -> {
      it.advance();
      return it.key();
    }).limit(map.size());
  }

  @NotNull
  public static IntStream stream(@NotNull TIntArrayList list) {
    if (list.isEmpty()) return IntStream.empty();
    return IntStream.range(0, list.size()).map(list::get);
  }

  @Nullable
  public static IntSet intersect(IntSet @NotNull ... sets) {
    Arrays.sort(sets, (set1, set2) -> {
      if (set1 == null) return -1;
      if (set2 == null) return 1;
      return set1.size() - set2.size();
    });
    IntSet result = null;
    for (IntSet set : sets) {
      result = intersect(result, set);
    }

    return result;
  }

  public static boolean intersects(@NotNull IntSet set1, @NotNull IntSet set2) {
    if (set1.size() <= set2.size()) {
      IntIterator it = set1.intIterator();
      while (it.hasNext()) {
        int value = it.nextInt();
        if (set2.contains(value)) return true;
      }
      return false;
    }
    return intersects(set2, set1);
  }

  @Contract("null, null -> null; !null, _ -> !null; _, !null -> !null")
  @Nullable
  public static TIntHashSet intersect(@Nullable TIntHashSet set1, @Nullable TIntHashSet set2) {
    if (set1 == null) return set2;
    if (set2 == null) return set1;

    TIntHashSet result = new TIntHashSet();

    if (set1.size() < set2.size()) {
      intersectTo(set1, set2, result);
    }
    else {
      intersectTo(set2, set1, result);
    }

    return result;
  }

  @Contract("null, null -> null; !null, _ -> !null; _, !null -> !null")
  @Nullable
  public static IntSet intersect(@Nullable IntSet set1, @Nullable IntSet set2) {
    if (set1 == null) return set2;
    if (set2 == null) return set1;

    IntSet result = new IntOpenHashSet();
    if (set1.size() < set2.size()) {
      intersectTo(set1, set2, result);
    }
    else {
      intersectTo(set2, set1, result);
    }

    return result;
  }

  private static void intersectTo(@NotNull TIntHashSet small, @NotNull TIntHashSet big, @NotNull TIntHashSet result) {
    small.forEach(value -> {
      if (big.contains(value)) {
        result.add(value);
      }
      return true;
    });
  }

  private static void intersectTo(@NotNull IntSet small, @NotNull IntSet big, @NotNull IntSet result) {
    for (IntIterator iterator = small.iterator(); iterator.hasNext(); ) {
      int value = iterator.nextInt();
      if (big.contains(value)) {
        result.add(value);
      }
    }
  }

  public static void addAll(@NotNull TIntHashSet where, @NotNull TIntHashSet what) {
    what.forEach(value -> {
      where.add(value);
      return true;
    });
  }

  public static void addAll(@NotNull TIntHashSet where, @NotNull Collection<Integer> what) {
    what.forEach(value -> where.add(value));
  }

  public static void addAll(@NotNull Collection<? super Integer> where, @NotNull TIntHashSet what) {
    what.forEach(value -> where.add(value));
  }

  public static <V> void putAll(@NotNull TIntObjectHashMap<? super V> where, @NotNull TIntObjectHashMap<? extends V> what) {
    what.forEachEntry((index, value) -> {
      where.put(index, value);
      return true;
    });
  }

  @NotNull
  public static IntSet union(@NotNull Collection<? extends IntSet> sets) {
    IntSet result = new IntOpenHashSet();
    for (IntSet set : sets) {
      result.addAll(set);
    }
    return result;
  }

  @NotNull
  public static IntStream stream(@NotNull TIntHashSet set) {
    TIntIterator it = set.iterator();
    return IntStream.generate(it::next).limit(set.size());
  }

  @NotNull
  public static <T> List<T> map2List(@NotNull TIntHashSet set, @NotNull IntFunction<? extends T> function) {
    return stream(set).mapToObj(function).collect(Collectors.toList());
  }

  @NotNull
  public static <T> TIntObjectHashMap<T> map2MapNotNull(@NotNull TIntHashSet set, @NotNull IntFunction<? extends T> function) {
    TIntObjectHashMap<T> result = new TIntObjectHashMap<>();
    set.forEach(it -> {
      T value = function.apply(it);
      if (value != null) {
        result.put(it, value);
      }
      return true;
    });
    return result;
  }

  @NotNull
  public static <T> IntSet map2IntSet(@NotNull Collection<? extends T> collection, @NotNull ToIntFunction<? super T> function) {
    IntOpenHashSet result = new IntOpenHashSet();
    for (T t : collection) {
      result.add(function.applyAsInt(t));
    }
    return result;
  }

  @NotNull
  public static <T> Map<T, IntSet> groupByAsIntSet(@NotNull IntCollection collection, @NotNull IntFunction<? extends T> function) {
    Map<T, IntSet> result = new HashMap<>();
    collection.forEach((IntConsumer)(it) -> {
      T key = function.apply(it);
      IntSet values = result.get(key);
      if (values == null) {
        values = new IntOpenHashSet();
        result.put(key, values);
      }
      values.add(it);
    });
    return result;
  }

  public static void processBatches(@NotNull IntStream stream,
                                    int batchSize,
                                    @NotNull ThrowableConsumer<? super TIntHashSet, ? extends VcsException> consumer)
    throws VcsException {
    Ref<TIntHashSet> batch = new Ref<>(new TIntHashSet());
    Ref<VcsException> exception = new Ref<>();
    stream.forEach(commit -> {
      batch.get().add(commit);
      if (batch.get().size() >= batchSize) {
        try {
          consumer.consume(batch.get());
        }
        catch (VcsException e) {
          exception.set(e);
        }
        finally {
          batch.set(new TIntHashSet());
        }
      }
    });

    if (!batch.get().isEmpty()) {
      consumer.consume(batch.get());
    }

    if (!exception.isNull()) throw exception.get();
  }

  @NotNull
  public static TIntHashSet singleton(@NotNull Integer elements) {
    TIntHashSet commits = new TIntHashSet();
    commits.add(elements);
    return commits;
  }

  public static <T> void add(@NotNull Map<? super T, TIntHashSet> targetMap, @NotNull T key, int value) {
    TIntHashSet set = targetMap.computeIfAbsent(key, __ -> new TIntHashSet());
    set.add(value);
  }

  public static boolean removeAll(@NotNull TIntHashSet fromWhere, @NotNull TIntHashSet what) {
    Ref<Boolean> result = new Ref<>(false);
    what.forEach(it -> {
      if (fromWhere.remove(it)) {
        result.set(true);
      }
      return true;
    });
    return result.get();
  }
}
