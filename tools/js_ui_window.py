"""Native desktop window for the JS dashboard Java2D preview."""
import sys
import tempfile
import threading
from pathlib import Path

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from js_ui_preview import (LANGUAGES, PAGES, STATES, THEMES, THEME_IDS, VEHICLES, Renderer,
                           overview_page_count, source_stamp, vehicle_pages)


class DesktopPreview:
    def __init__(self, root, folder):
        self.root = root
        self.folder = Path(folder)
        self.engine = Renderer(folder)
        self.lock = threading.Lock()
        self.busy = False
        self.pending = None
        self.generation = 0
        self.error = None
        self.image = None
        self.last_shown = None
        self.capture_toast = None
        self.capture_photo = None
        self.capture_png = None
        self.stamp = source_stamp()
        self.page = tk.StringVar(value=PAGES[0])
        self.state = tk.StringVar(value='received')
        self.language = tk.StringVar(value='ko')
        self.theme = tk.StringVar(value=THEMES[0])
        self.vehicle = tk.StringVar(value='js')
        self.vehicle_label = tk.StringVar(value=VEHICLES['js']['name'])
        self.overview = tk.StringVar(value='1')
        self.status = tk.StringVar(value='준비 중…')

        root.title('Dashboard · Java2D 데스크톱 프리뷰')
        root.configure(bg='#171a20')
        controls = ttk.Frame(root, padding=(12, 10))
        controls.pack(fill='x')
        first_row = ttk.Frame(controls)
        first_row.pack(fill='x', pady=(0, 7))
        second_row = ttk.Frame(controls)
        second_row.pack(fill='x')
        ttk.Label(first_row, text='화면').pack(side='left')
        self.page_box = ttk.Combobox(
            first_row, values=PAGES, textvariable=self.page, state='readonly', width=13
        )
        self.page_box.pack(side='left', padx=(5, 12))
        ttk.Label(first_row, text='언어').pack(side='left')
        self.language_box = ttk.Combobox(
            first_row, values=LANGUAGES, textvariable=self.language, state='readonly', width=6
        )
        self.language_box.pack(side='left', padx=(5, 12))
        ttk.Label(first_row, text='테마').pack(side='left')
        self.theme_box = ttk.Combobox(
            first_row, values=THEMES, textvariable=self.theme, state='readonly', width=13
        )
        self.theme_box.pack(side='left', padx=(5, 12))
        ttk.Label(second_row, text='차량').pack(side='left')
        self.vehicle_box = ttk.Combobox(second_row, textvariable=self.vehicle_label,
                                        state='readonly', width=16)
        self.vehicle_box.pack(side='left', padx=(5, 12))
        ttk.Label(second_row, text='조망 페이지').pack(side='left')
        self.overview_box = ttk.Combobox(second_row, values=('1',), textvariable=self.overview,
                                         state='disabled', width=3)
        self.overview_box.pack(side='left', padx=(5, 12))
        self.screen = ttk.Label(root, anchor='center')
        self.screen.pack(padx=12, pady=(0, 6))
        actions = ttk.Frame(root, padding=(12, 0, 12, 6))
        actions.pack(fill='x')
        ttk.Label(actions, text='데이터').pack(side='left')
        self.state_box = ttk.Combobox(actions, values=STATES, textvariable=self.state,
                                      state='readonly', width=12)
        self.state_box.pack(side='left', padx=(5, 12))
        ttk.Button(actions, text='현재 갱신 (R)', command=self.refresh).pack(side='left', padx=4)
        ttk.Button(actions, text='전체 갱신 (Shift+R)', command=self.refresh_all).pack(side='left', padx=4)
        self.capture_button = ttk.Button(actions, text='프리뷰 캡처…', command=self.capture, state='disabled')
        self.capture_button.pack(side='left', padx=4)
        ttk.Label(root, textvariable=self.status, padding=(12, 4)).pack(fill='x')

        for box in (self.page_box, self.state_box, self.language_box, self.overview_box):
            box.bind('<<ComboboxSelected>>', lambda _event: self.refresh())
        self.theme_box.bind('<<ComboboxSelected>>', self.theme_changed)
        self.vehicle_box.bind('<<ComboboxSelected>>', self.vehicle_changed)
        self.update_vehicle_labels()
        self.update_vehicle_pages()
        self.update_overview_control()
        root.bind('<KeyPress-r>', lambda _event: self.refresh())
        root.bind('<Shift-KeyPress-R>', lambda _event: self.refresh_all())
        root.protocol('WM_DELETE_WINDOW', self.close)
        self.refresh()
        self.poll()

    def selection(self):
        return (PAGES.index(self.page.get()), self.state.get(), self.language.get(),
                THEME_IDS[self.theme.get()], self.overview_box.current(), self.vehicle.get())

    def update_vehicle_labels(self):
        labels = tuple(VEHICLES[vehicle]['name'] for vehicle in VEHICLES)
        self.vehicle_labels = dict(zip(labels, VEHICLES))
        self.vehicle_box.configure(values=labels)

    def update_overview_control(self):
        count = overview_page_count(THEME_IDS[self.theme.get()])
        self.overview_box.configure(values=tuple(str(i + 1) for i in range(count)))
        if self.overview_box.current() < 0 or self.overview_box.current() >= count:
            self.overview_box.current(0)
        self.overview_box.configure(state='readonly' if count > 1 else 'disabled')

    def theme_changed(self, _event=None):
        self.update_overview_control()
        self.refresh()

    def update_vehicle_pages(self):
        choices = tuple(PAGES[index] for index in vehicle_pages(self.vehicle.get()))
        self.page_box.configure(values=choices)
        if self.page.get() not in choices:
            self.page.set(choices[0])

    def vehicle_changed(self, _event=None):
        self.vehicle.set(self.vehicle_labels[self.vehicle_label.get()])
        self.update_vehicle_pages()
        self.refresh()

    def request(self, all_screens=False):
        with self.lock:
            if self.busy:
                self.pending = (all_screens or bool(self.pending and self.pending[0]), self.selection())
                return
            self.busy = True
        threading.Thread(target=self.run, args=(all_screens, self.selection()), daemon=True).start()

    def refresh(self):
        self.capture_button.configure(state='disabled')
        self.status.set('선택한 화면 렌더링 중…')
        self.request()

    def refresh_all(self):
        self.capture_button.configure(state='disabled')
        self.status.set('전체 화면·상태·언어 갱신 중…')
        self.request(True)

    def capture(self):
        key = self.last_shown
        if key is None or key[:6] != self.selection():
            return
        page, state, language, theme_id, _overview_page, vehicle = key[:6]
        image_path = self.folder / f'{vehicle}-{page}-{state}-{language}-{theme_id}.png'
        try:
            png = image_path.read_bytes()
        except OSError as exc:
            messagebox.showerror('프리뷰 캡처 실패', str(exc), parent=self.root)
            return
        self.capture_png = png
        self.capture_name = f'dashboard-{vehicle}-{page}-{state}-{language}-{theme_id}.png'
        self.show_capture_toast()

    def show_capture_toast(self):
        if self.capture_toast is not None:
            self.capture_toast.destroy()
        image_path = self.folder / 'latest-capture.png'
        image_path.write_bytes(self.capture_png)
        toast = tk.Toplevel(self.root)
        toast.overrideredirect(True)
        toast.attributes('-topmost', True)
        toast.configure(bg='#252a33')
        self.capture_toast = toast
        self.capture_photo = tk.PhotoImage(file=str(image_path)).subsample(4, 4)
        ttk.Label(toast, text='스크린샷 캡처됨', padding=(10, 8)).pack(anchor='w')
        ttk.Label(toast, image=self.capture_photo, padding=(8, 0)).pack(anchor='w')
        actions = ttk.Frame(toast, padding=8)
        actions.pack(fill='x')
        ttk.Button(actions, text='클립보드 복사', command=self.copy_capture).pack(side='left', padx=(0, 6))
        ttk.Button(actions, text='저장…', command=self.save_capture).pack(side='left')
        toast.update_idletasks()
        x = max(0, toast.winfo_screenwidth() - toast.winfo_width() - 18)
        y = max(0, toast.winfo_screenheight() - toast.winfo_height() - 48)
        toast.geometry(f'+{x}+{y}')
        toast.after(10000, lambda: self.dismiss_capture_toast(toast))

    def dismiss_capture_toast(self, toast):
        if self.capture_toast is toast and toast.winfo_exists():
            toast.destroy()
            self.capture_toast = None
            self.capture_photo = None

    def copy_capture(self):
        try:
            with tempfile.NamedTemporaryFile(suffix='.png', dir=self.folder, delete=False) as image:
                image.write(self.capture_png)
                image_path = Path(image.name)
            try:
                self.engine.copy_png_to_clipboard(image_path)
            finally:
                image_path.unlink(missing_ok=True)
            self.status.set('스크린샷을 클립보드에 복사했습니다.')
            if self.capture_toast is not None:
                self.capture_toast.after(900, self.capture_toast.destroy)
                self.capture_toast = None
                self.capture_photo = None
        except Exception as exc:
            messagebox.showerror('클립보드 복사 실패', str(exc), parent=self.root)

    def save_capture(self):
        destination = filedialog.asksaveasfilename(
            parent=self.root,
            title='프리뷰 렌더링 캡처 저장',
            initialfile=self.capture_name,
            defaultextension='.png',
            filetypes=(('PNG 이미지', '*.png'),),
        )
        if not destination:
            return
        try:
            Path(destination).write_bytes(self.capture_png)
            if self.capture_toast is not None:
                self.capture_toast.after(900, self.capture_toast.destroy)
                self.capture_toast = None
                self.capture_photo = None
        except OSError as exc:
            messagebox.showerror('프리뷰 캡처 실패', str(exc), parent=self.root)

    def run(self, all_screens, selected):
        while True:
            try:
                page, state, language, theme_id, overview_page, vehicle = selected
                self.engine.overview_page = overview_page
                with tempfile.TemporaryDirectory(prefix='js-ui-render-') as work:
                    self.engine.render(
                        work,
                        vehicle_pages(vehicle) if all_screens else (page,),
                        STATES if all_screens else (state,),
                        LANGUAGES if all_screens else (language,),
                        THEMES if all_screens else (theme_id,),
                        vehicle=vehicle,
                    )
                    for image in Path(work).glob('*.png'):
                        image.replace(self.folder / image.name)
                error = None
            except Exception as exc:
                error = str(exc)
                print(error, file=sys.stderr)

            with self.lock:
                self.error = error
                self.generation += 1
                pending = self.pending
                self.pending = None
                if not pending:
                    self.busy = False
                    return
                all_screens, selected = pending

    def poll(self):
        stamp = source_stamp()
        if stamp != self.stamp:
            self.stamp = stamp
            self.request()

        with self.lock:
            busy, error, generation = self.busy, self.error, self.generation
        ready = (not busy and not error and self.last_shown is not None
                 and self.last_shown[:6] == self.selection()
                 and self.last_shown[6] == generation)
        self.capture_button.configure(state='normal' if ready else 'disabled')
        if error:
            self.status.set('렌더링 오류: ' + error)
        elif busy:
            self.status.set('렌더링 중…')
        else:
            self.status.set('PC Java2D · 예시 데이터 | R: 현재 갱신 · Shift+R: 전체 갱신')
            key = (*self.selection(), generation)
            if key != self.last_shown:
                page, state, language, theme_id, _overview_page, vehicle = key[:6]
                path = self.folder / f'{vehicle}-{page}-{state}-{language}-{theme_id}.png'
                if path.exists():
                    self.image = tk.PhotoImage(file=str(path))
                    self.screen.configure(image=self.image)
                    self.last_shown = key

        self.root.after(100, self.poll)

    def close(self):
        self.engine.close()
        self.root.destroy()


def run():
    with tempfile.TemporaryDirectory(prefix='js-ui-preview-') as folder:
        root = tk.Tk()
        DesktopPreview(root, folder)
        root.mainloop()
