# SPDX-FileCopyrightText: 2026 Rime community
# SPDX-License-Identifier: GPL-3.0-or-later

function(apply_librime_chen_patch)
  get_filename_component(jni_dir "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/.." ABSOLUTE)
  set(source_dir "${jni_dir}/librime")
  set(patch_file "${jni_dir}/cmake/librime-chen-exact-first.patch")
  # Android may configure multiple ABIs concurrently against the same sources.
  file(MAKE_DIRECTORY "${source_dir}/build")
  file(LOCK "${source_dir}/build/chen-exact-first.lock" GUARD FUNCTION TIMEOUT 120)
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${patch_file}")

  find_program(CHEN_GIT_EXECUTABLE git)
  if(CHEN_GIT_EXECUTABLE)
    set(check_command "${CHEN_GIT_EXECUTABLE}" apply --check)
    set(reverse_command "${CHEN_GIT_EXECUTABLE}" apply --reverse --check)
    set(apply_command "${CHEN_GIT_EXECUTABLE}" apply)
  else()
    find_program(CHEN_PATCH_EXECUTABLE patch REQUIRED)
    set(check_command "${CHEN_PATCH_EXECUTABLE}" -p1 --batch --forward --dry-run -i)
    set(reverse_command "${CHEN_PATCH_EXECUTABLE}" -p1 --force --reverse --dry-run -i)
    set(apply_command "${CHEN_PATCH_EXECUTABLE}" -p1 --batch --forward -i)
  endif()

  execute_process(COMMAND ${reverse_command} "${patch_file}"
                  WORKING_DIRECTORY "${source_dir}" RESULT_VARIABLE reversed
                  OUTPUT_QUIET ERROR_QUIET)
  if(reversed EQUAL 0)
    message(STATUS "librime Chen exact-first patch already applied")
    return()
  endif()
  execute_process(COMMAND ${check_command} "${patch_file}"
                  WORKING_DIRECTORY "${source_dir}" RESULT_VARIABLE checked
                  OUTPUT_VARIABLE output ERROR_VARIABLE error)
  if(NOT checked EQUAL 0)
    message(FATAL_ERROR "Cannot apply librime Chen exact-first patch:\n${output}${error}")
  endif()
  execute_process(COMMAND ${apply_command} "${patch_file}"
                  WORKING_DIRECTORY "${source_dir}" RESULT_VARIABLE applied
                  OUTPUT_VARIABLE output ERROR_VARIABLE error)
  if(NOT applied EQUAL 0)
    message(FATAL_ERROR "Failed to apply librime Chen exact-first patch:\n${output}${error}")
  endif()
  message(STATUS "Applied librime Chen exact-first patch")
endfunction()

apply_librime_chen_patch()
