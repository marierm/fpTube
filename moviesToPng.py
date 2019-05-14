#!/usr/bin/env python3

import subprocess, os, sys, glob
from PIL import Image

fileWidth = 200
fileHeight = 120

loResPath = os.path.abspath('02-videos_200x120')
pngPath = os.path.abspath('03-videos_png')
videos_colPath = os.path.abspath('videos_col')

# print(loResPath)

for i, file in enumerate(sys.argv[1:]):
    noExtension = os.path.splitext(os.path.basename(file))[0]
    extension = os.path.splitext(file)[-1]
    loResFile = os.path.join(loResPath, noExtension+"_200x120"+extension) 
    directory = os.path.join(pngPath, noExtension)
    if not os.path.exists(directory):
        os.makedirs(directory)
    # print(*["ffmpeg", "-i", file, "-s", "200x120", os.path.join(loResPath, loResFile) , "-hide_banner"])
    subprocess.run(["ffmpeg", "-i", file, "-s", "200x120", loResFile])
    subprocess.run(["ffmpeg", "-i", loResFile, os.path.join(pngPath, noExtension, "%06d.png") , "-hide_banner"])

    if not os.path.exists(os.path.join(videos_colPath, noExtension)):
        os.makedirs(os.path.join(videos_colPath, noExtension))
    files = sorted(glob.glob(os.path.join(pngPath, noExtension, '*.png')))
    images = [ Image.open(pngFile) for pngFile in files]
    results = []
    for i in range(fileWidth):
        columns = [ img.crop((i,0,i+1,fileHeight)) for img in images ]
        res = Image.new('RGB', ( len(files), fileHeight ))
        for i, col in enumerate(columns):
            res.paste(col, (i,0))
        results.append(res)
        
    for i, img in enumerate(results):
        img.save(os.path.join(videos_colPath, noExtension, "%03d.png" % i))




