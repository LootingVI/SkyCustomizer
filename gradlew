#!/bin/sh
# minimal wrapper - delegates to gradle if available
exec gradle "$@"
