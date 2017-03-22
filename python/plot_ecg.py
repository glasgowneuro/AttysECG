# plots all ECG channels
# recorded with the Attys: www.attys.tech
#
import numpy as np
import pylab as pl
#
data = np.loadtxt('demo_ecg.tsv');
#
fig = pl.figure(1)
#
a =  100
b = 1000
ymin = -0.0015
ymax = 0.0015
pl.title('ECG measured with the Attys');
# I
pl.subplot(611);
pl.plot(data[a:b,0],data[a:b,1]);
pl.xlabel('time/sec');
pl.ylabel('I/V');
pl.ylim(ymin,ymax);
# II
pl.subplot(612);
pl.plot(data[a:b,0],data[a:b,2]);
pl.xlabel('time/sec');
pl.ylabel('II/V');
pl.ylim(ymin,ymax);
# III
pl.subplot(613);
pl.plot(data[a:b,0],data[a:b,3]);
pl.xlabel('time/sec');
pl.ylabel('III/V');
pl.ylim(ymin,ymax);
#
# aVR
pl.subplot(614);
pl.plot(data[a:b,0],data[a:b,4]);
pl.xlabel('time/sec');
pl.ylabel('aVR/V');
pl.ylim(ymin,ymax);
#
# aVL
pl.subplot(615);
pl.plot(data[a:b,0],data[a:b,5]);
pl.xlabel('time/sec');
pl.ylabel('aVL/V');
pl.ylim(ymin,ymax);
# aVF
pl.subplot(616);
pl.plot(data[a:b,0],data[a:b,6]);
pl.xlabel('time/sec');
pl.ylabel('aVF/V');
pl.ylim(ymin,ymax);
pl.show();
