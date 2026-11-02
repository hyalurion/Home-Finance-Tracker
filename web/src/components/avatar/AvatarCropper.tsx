'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { Dialog } from '@/components/ui/Dialog';

/** Edge length of the produced square image (the avatar is shown in a circle). */
const OUTPUT_SIZE = 256;

const MIN_ZOOM = 1;
const MAX_ZOOM = 3;

interface Props {
  open: boolean;
  /** The decoded source image. Null until a file has been picked. */
  image: HTMLImageElement | null;
  onCancel: () => void;
  /** Receives the cropped PNG as a data URL. */
  onConfirm: (dataUrl: string) => void;
}

/**
 * Circular avatar cropper.
 *
 * Ported from the original Vue component (client/src/components/AvatarUpload.vue):
 * the image is drawn cover-fit into a square canvas, the user can zoom
 * (1x - 3x) and drag it around, and the result is masked into a circle.
 */
export function AvatarCropper({ open, image, onCancel, onConfirm }: Props) {
  const { t } = useI18n();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [zoom, setZoom] = useState(MIN_ZOOM);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const dragRef = useRef({ active: false, x: 0, y: 0 });

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas || !image) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const size = canvas.width;
    ctx.clearRect(0, 0, size, size);

    // Cover-fit: the image always fills the whole canvas.
    const ratio = image.width / image.height;
    let displayWidth: number;
    let displayHeight: number;
    if (ratio > 1) {
      displayHeight = size;
      displayWidth = displayHeight * ratio;
    } else {
      displayWidth = size;
      displayHeight = displayWidth / ratio;
    }
    displayWidth *= zoom;
    displayHeight *= zoom;

    const x = (size - displayWidth) / 2 + offset.x;
    const y = (size - displayHeight) / 2 + offset.y;
    ctx.drawImage(image, x, y, displayWidth, displayHeight);

    // Mask the corners away so the avatar is a circle.
    ctx.save();
    ctx.globalCompositeOperation = 'destination-in';
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }, [image, zoom, offset]);

  // Reset the view whenever the cropper opens.
  useEffect(() => {
    if (!open) return;
    setZoom(MIN_ZOOM);
    setOffset({ x: 0, y: 0 });
  }, [open, image]);

  useEffect(() => {
    if (open) draw();
  }, [open, draw]);

  const onPointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    dragRef.current = { active: true, x: e.clientX, y: e.clientY };
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const onPointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!dragRef.current.active) return;
    const dx = e.clientX - dragRef.current.x;
    const dy = e.clientY - dragRef.current.y;
    dragRef.current = { active: true, x: e.clientX, y: e.clientY };
    setOffset((prev) => ({ x: prev.x + dx, y: prev.y + dy }));
  };

  const endDrag = (e: React.PointerEvent<HTMLCanvasElement>) => {
    dragRef.current.active = false;
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId);
    }
  };

  const confirm = () => {
    const canvas = canvasRef.current;
    if (!canvas || !image) return;
    // Make sure the canvas holds the latest view before exporting.
    draw();
    onConfirm(canvas.toDataURL('image/png'));
  };

  return (
    <Dialog
      open={open}
      title={t('avatar.cropTitle')}
      onClose={onCancel}
      width={420}
      footer={
        <>
          <button className="btn" onClick={onCancel}>{t('avatar.cancel')}</button>
          <button className="btn btn--primary" onClick={confirm} disabled={!image}>{t('avatar.confirm')}</button>
        </>
      }
    >
      <div className="cropper">
        <canvas
          ref={canvasRef}
          className="cropper__canvas"
          width={OUTPUT_SIZE}
          height={OUTPUT_SIZE}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
        />

        <div className="cropper__controls">
          <label className="cropper__zoom-label" htmlFor="avatar-zoom">
            {t('avatar.zoom')}
          </label>
          <input
            id="avatar-zoom"
            className="range"
            type="range"
            min={MIN_ZOOM}
            max={MAX_ZOOM}
            step={0.01}
            value={zoom}
            onChange={(e) => setZoom(Number(e.target.value))}
          />
          <span className="cropper__zoom-value num">{zoom.toFixed(1)}x</span>
        </div>
      </div>
    </Dialog>
  );
}
