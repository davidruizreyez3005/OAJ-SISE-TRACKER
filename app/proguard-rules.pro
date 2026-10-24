# Project-specific R8 rules. Libraries in use ship their own consumer rules.

# Keep class and member names, and line numbers. The app has no crash
# reporting (hard rule 6), so a stack trace from a team member's phone has to
# be readable as it is, and nothing that finds a class or file by name can
# break in a release build only.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
