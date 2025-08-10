# key-abbrevmgr

Abbreviations are built-in to the KeY system, but this construct is not very popular. This may be to their volatility: you are not able to store, and reuse them on new proof attempts.
This PR brings a new widget:

![image](https://github.com/KeYProject/key/assets/104259/8583b869-4782-420f-86a5-a733729ffda7)

The widget offers following features:

* view current defined abbreviations
* load and store abbreviation mappings
    * A text file where each line `<label>::==<term>` describes an abbreviation.
* transfer (import) abbreviations of currently open proofs
* Context menu:
    * change the abbreviation label
    * delete an abbreviation
    * disable an abbreviate

