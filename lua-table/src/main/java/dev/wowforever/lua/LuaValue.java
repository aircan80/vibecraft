package dev.wowforever.lua;

/** A Lua value as produced by the evaluator. */
public sealed interface LuaValue
    permits LuaNil, LuaBoolean, LuaNumber, LuaString, LuaTable, LuaUnresolved {}
