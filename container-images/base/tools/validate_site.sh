#!/bin/bash

dir=$1
target_uid=$2
target_gid=$3

check_fs_permissions(){
    actual_uid=$(ls -lnd $dir | tr -s ' ' | cut -d ' ' -f 3)
    actual_gid=$(ls -lnd $dir | tr -s ' ' | cut -d ' ' -f 4)

    if [ ! -d "$dir" ]; then
        echo "The provided site seems to be invalid. Missing: $dir"
        return 1
    fi

    if [[ "$actual_uid" != "$target_uid" ]]; then
        echo "The provided Gerrit site is not owned by the correct UID."
        echo "$dir should be owned by user $target_uid, but is owned by $actual_uid"
        return 1
    fi

    if [[ "$actual_gid" != "$target_gid" ]]; then
        echo "The provided Gerrit site is not owned by the correct GID."
        echo "$dir should be owned by group $target_gid, but is owned by $actual_gid"
        return 1
    fi

    if [ ! -r "$dir" ]; then
        echo "Cannot read $dir."
        return 1
    fi

    if [ ! -w "$dir" ]; then
        echo "Cannot write in $dir."
        return 1
    fi

    return 0
}

fix_fs_permissions(){
    echo "Trying to fix file permissions"
    chown -R $target_uid:$target_gid $dir
    chmod -R 755 $dir
    check_fs_permissions || {
        echo "Failed to fix file permissions. Please fix them manually on the host system.";
        exit 1;
    }
    echo "Success!"
    echo ""
}

check_fs_permissions || {
    [[ "$FIXFS" == "true" ]] && fix_fs_permissions
}
