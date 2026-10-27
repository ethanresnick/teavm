#include <file-links.h>
#include <stdint.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>

// The paths are null-terminated byte strings.
int32_t teavmTestSymlink(int8_t* target, int8_t* link) {
    return symlink((const char*) target, (const char*) link) == 0;
}

// The permission bits of the entry at the path (not following a symbolic link), or -1 if there is none.
int32_t teavmTestFileMode(int8_t* path) {
    struct stat s;
    if (lstat((const char*) path, &s) != 0) {
        return -1;
    }
    return (int32_t) (s.st_mode & 07777);
}

int32_t teavmTestUmask() {
    mode_t mask = umask(0);
    umask(mask);
    return (int32_t) mask;
}

int32_t teavmTestUnlink(int8_t* path) {
    return unlink((const char*) path) == 0;
}
