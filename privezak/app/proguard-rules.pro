# ML Kit reaches its component registrars only through names listed as manifest meta-data, so R8
# sees nothing referencing them and strips them. Without these the barcode scanner comes back null.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
