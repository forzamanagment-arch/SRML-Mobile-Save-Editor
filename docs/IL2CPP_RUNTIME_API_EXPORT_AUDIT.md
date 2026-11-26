# IL2CPP Runtime API export audit

Date: 2026-11-26

## Result

The successful GitHub Actions run `45829217381` produced artifact `srml-il2cpp-metadata-analysis` (artifact ID `15121237036`). The ELF symbol reports were generated from the game's `libil2cpp.so`.

The dynamic symbol table contains **241 exported symbols beginning with `il2cpp_`**. This is stronger evidence than metadata strings: these symbols are exported by the native ELF and can in principle be resolved by a component already executing in the same process.

The 21-symbol probe in `runtime-helpers/IL2CPP_RUNTIME_API_EXPORTS.txt` reports all 21 requested functions as FOUND.

## Useful exported API groups

- Runtime and threads: `il2cpp_init`, `il2cpp_domain_get`, `il2cpp_thread_attach`, `il2cpp_thread_current`, `il2cpp_thread_detach`, `il2cpp_is_vm_thread`.
- Assemblies and images: `il2cpp_domain_get_assemblies`, `il2cpp_domain_assembly_open`, `il2cpp_assembly_get_image`, `il2cpp_image_get_class_count`, `il2cpp_image_get_class`.
- Class and method discovery: `il2cpp_class_from_name`, `il2cpp_class_get_method_from_name`, `il2cpp_class_get_field_from_name`, `il2cpp_class_get_methods`, `il2cpp_class_get_fields`, `il2cpp_method_get_name`, `il2cpp_method_get_param_count`.
- Invocation and objects: `il2cpp_runtime_invoke`, `il2cpp_object_get_class`, `il2cpp_object_unbox`, `il2cpp_object_new`.
- Fields: `il2cpp_field_get_value`, `il2cpp_field_set_value`, `il2cpp_field_get_offset`, `il2cpp_field_static_get_value`, `il2cpp_field_static_set_value`.
- GC and object lifetime: `il2cpp_gchandle_new`, `il2cpp_gchandle_get_target`, `il2cpp_gchandle_free`, `il2cpp_gc_wbarrier_set_field`.
- Unity integration: `il2cpp_resolve_icall`, `il2cpp_unity_liveness_calculation_from_root`, `il2cpp_unity_liveness_calculation_from_statics`.

Additional symbols include `il2cpp_init` at ELF VA `0x29C03D4`, `il2cpp_shutdown` at `0x29C0478`, `il2cpp_domain_get` at `0x29C1694`, and `il2cpp_runtime_invoke` at `0x29C2884`.

## Important interpretation limits

1. Exported symbols do not grant an external process permission to call them. A component must already execute inside the game's process, or use a separately verified supported interface.
2. These ELF virtual addresses are not absolute runtime addresses. ASLR/load bias must be handled.
3. Presence of `JNI_OnLoad` in the same library's dynamic symbol report is not proof of a mod/plugin entry point. Its implementation and purpose have not yet been established.
4. Presence of `il2cpp_init` does not mean a second component should call it. The game already owns initialization; a bridge would attach to the existing runtime, not initialize a second VM.
5. The exported API does not prove that Unity APIs or game model methods are safe to invoke from an arbitrary native thread. Game-thread scheduling remains a requirement to investigate.
6. No injection, APK patching, or process-memory write path has been confirmed by this audit.

## Candidate read-only discovery path once in-process execution is available

1. Resolve the exported IL2CPP API from the loaded `libil2cpp.so`.
2. Obtain the existing domain with `il2cpp_domain_get`.
3. Attach the bridge thread with `il2cpp_thread_attach` if it is not already attached.
4. Enumerate assemblies/images and resolve the game assembly and class metadata.
5. Find the Unity `SceneContext` component through a verified Unity-side mechanism, then read its `PlayerState` reference.
6. Read the `PlayerState` model reference and current currency through known accessors.
7. Report the value to the overlay over a narrow IPC channel.

This is a research sequence, not an implemented bridge. The first runtime test should be read-only.

## Next investigation

The unresolved blocker is the bootstrap/loading route: how a native bridge could be loaded into this particular Unity Android application through a supported or controlled test build. Inspect the APK manifest, Unity player loading path, native library packaging/loading behavior, and the semantics of existing JNI entry points. Do not treat a generic `JNI_OnLoad` export or the presence of IL2CPP APIs as proof that an external overlay can load code into the game.
