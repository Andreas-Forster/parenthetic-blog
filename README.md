# Parenthetic Blog

This is just one entry of maybe many ones listed [here](https://github.com/Andreas-Forster/parenthetic-blog)

## How to add landmarks to the Basel Face Model 2019

- Background about the model [BFM2019](https://faces.dmi.unibas.ch/bfm/bfm2019.html) can be found on the page about [PMMs](https://gravis.dmi.unibas.ch/pmm).
- It is shown how to add new landmarks to the model:

    - Start **ClickModelLandmarks**.
    - Activate landmark clicking (top left, cross aim button) and click landmarks on the mesh (right click to rename them).
    - Right click landmarks group and select "_Save original landmarks ..._".
    - Start **AddLandmarksToModel**.
- All content is in the file: [DefineModelLandmarks.scala](https://github.com/Andreas-Forster/parenthetic-blog/blob/click-model-landmarks/src/main/scala/DefineModelLandmarks.scala)